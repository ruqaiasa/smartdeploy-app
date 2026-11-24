package com.smartdeploy.meeting;

import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Rendez-vous en visioconférence : l'organisateur propose jusqu'à trois créneaux, l'invité en choisit un (ou
 * refuse). La salle de visioconférence (Jitsi Meet) porte un nom long et aléatoire. Un développeur échange avec
 * l'équipe DevOps ; un DevOps ou un administrateur peut inviter n'importe quel utilisateur.
 */
@Service
public class MeetingService {

    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);
    static final Set<Role> TEAM = Set.of(Role.DEVOPS, Role.ADMIN);
    private static final DateTimeFormatter HUMAN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.FRENCH);

    public record ProposeRequest(String topic, String agenda, Long inviteeId, Long deploymentId, Long claimId,
                                 List<LocalDateTime> slots, Integer durationMinutes) {
    }

    public record Contact(Long id, String name, String email, String role) {
    }

    public record MeetingResponse(Long id, String room, String roomUrl, String topic, String agenda, String status,
                                  Contact organizer, Contact invitee, boolean organizedByMe, Long deploymentId, Long claimId,
                                  List<LocalDateTime> slots, LocalDateTime startsAt, int durationMinutes, LocalDateTime createdAt) {
    }

    private final MeetingRepository meetings;
    private final UserRepository users;
    private final MailService mail;
    private final String jitsiBase;
    private final String frontendUrl;

    public MeetingService(MeetingRepository meetings, UserRepository users, MailService mail,
                          @Value("${smartdeploy.meetings.jitsi-url:https://meet.jit.si}") String jitsiBase,
                          @Value("${smartdeploy.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.meetings = meetings;
        this.users = users;
        this.mail = mail;
        this.jitsiBase = jitsiBase.replaceAll("/+$", "");
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Les interlocuteurs possibles : l'équipe DevOps pour un développeur ; tout le monde pour l'équipe. */
    @Transactional(readOnly = true)
    public List<Contact> contacts(User me) {
        boolean team = TEAM.contains(me.getRole());
        return users.findAllByDeletedFalse().stream()
                .filter(User::isEnabled)
                .filter(u -> !Objects.equals(u.getId(), me.getId()))
                .filter(u -> team ? u.getRole() != Role.VIEWER : TEAM.contains(u.getRole()))
                .sorted(Comparator.comparing(User::getEmail))
                .map(MeetingService::contact)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MeetingResponse> mine(User me) {
        return meetings.findForUser(me.getId()).stream().map(m -> toResponse(m, me)).toList();
    }

    @Transactional
    public MeetingResponse propose(ProposeRequest r, User me) {
        if (r == null || r.topic() == null || r.topic().isBlank()) {
            throw new IllegalArgumentException("Le sujet est obligatoire");
        }
        List<LocalDateTime> slots = r.slots() == null ? List.of() : r.slots().stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (slots.isEmpty() || slots.size() > 3) {
            throw new IllegalArgumentException("Proposez entre un et trois créneaux");
        }
        if (slots.get(0).isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new IllegalArgumentException("Les créneaux doivent être dans le futur");
        }
        User invitee = users.findByIdAndDeletedFalse(r.inviteeId())
                .orElseThrow(() -> new IllegalArgumentException("Interlocuteur introuvable"));
        if (contacts(me).stream().noneMatch(c -> c.id().equals(invitee.getId()))) {
            throw new AccessDeniedException("Vous ne pouvez pas inviter cette personne");
        }
        Meeting m = new Meeting();
        m.setRoom("SmartDeploy-" + UUID.randomUUID().toString().replace("-", ""));
        m.setTopic(r.topic().trim());
        m.setAgenda(r.agenda() == null ? null : r.agenda().trim());
        m.setOrganizer(me);
        m.setInvitee(invitee);
        m.setDeploymentId(r.deploymentId());
        m.setClaimId(r.claimId());
        m.setSlot1(slots.get(0));
        m.setSlot2(slots.size() > 1 ? slots.get(1) : null);
        m.setSlot3(slots.size() > 2 ? slots.get(2) : null);
        m.setDurationMinutes(r.durationMinutes() == null ? 30 : Math.max(15, Math.min(r.durationMinutes(), 120)));
        m.setStatus(Meeting.Status.PROPOSED);
        m.setCreatedAt(LocalDateTime.now());
        Meeting saved = meetings.save(m);
        StringBuilder text = new StringBuilder(name(me) + " vous propose une visioconférence : « " + saved.getTopic() + " ».\n\nCréneaux proposés :\n");
        slots.forEach(s -> text.append(" - ").append(HUMAN.format(s)).append("\n"));
        text.append("\nChoisissez un créneau : ").append(frontendUrl).append("/meetings\n");
        notify(invitee, "SmartDeploy : proposition de visioconférence", text.toString());
        return toResponse(saved, me);
    }

    @Transactional
    public MeetingResponse accept(Long id, int slot, User me) {
        Meeting m = load(id);
        if (!Objects.equals(m.getInvitee().getId(), me.getId())) {
            throw new AccessDeniedException("Seule la personne invitée choisit un créneau");
        }
        if (m.getStatus() != Meeting.Status.PROPOSED) {
            throw new IllegalStateException("Ce rendez-vous n'attend plus de réponse");
        }
        LocalDateTime chosen = slots(m).size() >= slot && slot >= 1 ? slots(m).get(slot - 1) : null;
        if (chosen == null) {
            throw new IllegalArgumentException("Créneau inconnu");
        }
        m.setStartsAt(chosen);
        m.setStatus(Meeting.Status.CONFIRMED);
        notify(m.getOrganizer(), "SmartDeploy : visioconférence confirmée",
                name(me) + " a accepté la visioconférence « " + m.getTopic() + " » le " + HUMAN.format(chosen)
                        + ".\n\nRejoindre : " + frontendUrl + "/meetings/" + m.getId() + "\n");
        return toResponse(m, me);
    }

    @Transactional
    public MeetingResponse decline(Long id, User me) {
        Meeting m = load(id);
        boolean invitee = Objects.equals(m.getInvitee().getId(), me.getId());
        boolean organizer = Objects.equals(m.getOrganizer().getId(), me.getId());
        if (!invitee && !organizer) {
            throw new AccessDeniedException("Ce rendez-vous ne vous concerne pas");
        }
        if (m.getStatus() == Meeting.Status.DECLINED || m.getStatus() == Meeting.Status.CANCELLED) {
            throw new IllegalStateException("Ce rendez-vous est déjà clos");
        }
        m.setStatus(organizer ? Meeting.Status.CANCELLED : Meeting.Status.DECLINED);
        User other = organizer ? m.getInvitee() : m.getOrganizer();
        notify(other, "SmartDeploy : visioconférence " + (organizer ? "annulée" : "refusée"),
                name(me) + (organizer ? " a annulé" : " a refusé") + " la visioconférence « " + m.getTopic() + " ».\n");
        return toResponse(m, me);
    }

    @Transactional(readOnly = true)
    public MeetingResponse get(Long id, User me) {
        Meeting m = load(id);
        ensureParticipant(m, me);
        return toResponse(m, me);
    }

    /** Fichier d'agenda (iCalendar) d'un rendez-vous confirmé : Outlook, Google Agenda, Apple Calendrier. */
    @Transactional(readOnly = true)
    public String ics(Long id, User me) {
        Meeting m = load(id);
        ensureParticipant(m, me);
        if (m.getStartsAt() == null) {
            throw new IllegalStateException("Ce rendez-vous n'est pas encore confirmé");
        }
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
        String start = m.getStartsAt().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC")).format(f);
        String end = m.getStartsAt().plusMinutes(m.getDurationMinutes()).atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC")).format(f);
        String now = LocalDateTime.now().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of("UTC")).format(f);
        return String.join("\r\n", "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//SmartDeploy//Rendez-vous//FR", "BEGIN:VEVENT",
                "UID:meeting-" + m.getId() + "@smartdeploy", "DTSTAMP:" + now, "DTSTART:" + start, "DTEND:" + end,
                "SUMMARY:" + escape("SmartDeploy : " + m.getTopic()),
                "DESCRIPTION:" + escape((m.getAgenda() == null ? "" : m.getAgenda() + "\n\n") + "Visioconférence : " + roomUrl(m)),
                "LOCATION:" + escape(roomUrl(m)), "END:VEVENT", "END:VCALENDAR", "");
    }

    /* --------------------------------------------------------------------------------------------- */

    private Meeting load(Long id) {
        return meetings.findById(id).orElseThrow(() -> new NoSuchElementException("Rendez-vous introuvable"));
    }

    private static void ensureParticipant(Meeting m, User me) {
        if (!Objects.equals(m.getInvitee().getId(), me.getId()) && !Objects.equals(m.getOrganizer().getId(), me.getId())) {
            throw new AccessDeniedException("Ce rendez-vous ne vous concerne pas");
        }
    }

    static List<LocalDateTime> slots(Meeting m) {
        List<LocalDateTime> list = new ArrayList<>();
        list.add(m.getSlot1());
        if (m.getSlot2() != null) list.add(m.getSlot2());
        if (m.getSlot3() != null) list.add(m.getSlot3());
        return list;
    }

    private String roomUrl(Meeting m) {
        return jitsiBase + "/" + m.getRoom();
    }

    private void notify(User to, String subject, String text) {
        try {
            mail.send(to.getEmail(), subject, "Bonjour,\n\n" + text);
        } catch (RuntimeException exception) {
            log.warn("Rendez-vous : courriel non envoyé à {} ({})", to.getEmail(), exception.getMessage());
        }
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    private static String name(User u) {
        String n = ((u.getFirstName() == null ? "" : u.getFirstName()) + " " + (u.getLastName() == null ? "" : u.getLastName())).trim();
        return n.isEmpty() ? u.getEmail() : n;
    }

    private static Contact contact(User u) {
        return new Contact(u.getId(), name(u), u.getEmail(), u.getRole() == null ? null : u.getRole().name());
    }

    private MeetingResponse toResponse(Meeting m, User me) {
        return new MeetingResponse(m.getId(), m.getRoom(), roomUrl(m), m.getTopic(), m.getAgenda(), m.getStatus().name(),
                contact(m.getOrganizer()), contact(m.getInvitee()), Objects.equals(m.getOrganizer().getId(), me.getId()),
                m.getDeploymentId(), m.getClaimId(), slots(m), m.getStartsAt(), m.getDurationMinutes(), m.getCreatedAt());
    }
}
