package com.smartdeploy.meeting;

import com.smartdeploy.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Un rendez-vous en visioconférence entre un développeur et un DevOps : jusqu'à trois créneaux proposés, un choisi. */
@Entity
@Table(name = "meetings")
@Getter
@Setter
@NoArgsConstructor
public class Meeting {

    public enum Status { PROPOSED, CONFIRMED, DECLINED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Nom de la salle de visioconférence : long et aléatoire, il fait office de clé d'accès. */
    @Column(name = "room", nullable = false, unique = true, length = 80)
    private String room;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(length = 1000)
    private String agenda;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitee_id", nullable = false)
    private User invitee;

    @Column(name = "deployment_id")
    private Long deploymentId;

    @Column(name = "claim_id")
    private Long claimId;

    @Column(name = "slot1", nullable = false)
    private LocalDateTime slot1;

    @Column(name = "slot2")
    private LocalDateTime slot2;

    @Column(name = "slot3")
    private LocalDateTime slot3;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
