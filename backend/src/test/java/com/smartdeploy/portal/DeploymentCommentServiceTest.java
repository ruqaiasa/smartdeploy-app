package com.smartdeploy.portal;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Discussion d'une demande : qui y participe, et qui est prévenu d'un nouveau message. */
class DeploymentCommentServiceTest {

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@smartdeploy.local");
        u.setRole(role);
        return u;
    }

    private final User developer = user(1, Role.DEVELOPER);
    private final User otherDeveloper = user(2, Role.DEVELOPER);
    private final User devops = user(3, Role.DEVOPS);
    private final User admin = user(4, Role.ADMIN);

    private Deployment requestBy(User requester) {
        Deployment d = mock(Deployment.class);
        when(d.getRequestedBy()).thenReturn(requester);
        return d;
    }

    @Test
    void onlyTheRequesterAndTheTeamTakePart() {
        Deployment request = requestBy(developer);
        assertTrue(DeploymentCommentService.isParticipant(request, developer));
        assertTrue(DeploymentCommentService.isParticipant(request, devops));
        assertTrue(DeploymentCommentService.isParticipant(request, admin));
        assertFalse(DeploymentCommentService.isParticipant(request, otherDeveloper), "un autre développeur n'y a pas accès");
        assertFalse(DeploymentCommentService.isParticipant(request, user(5, Role.VIEWER)));
    }

    @Test
    void aMessageWarnsTheOtherSideButNeverItsAuthor() {
        UserRepository users = mock(UserRepository.class);
        when(users.findAllByDeletedFalse()).thenReturn(List.of(developer, otherDeveloper, devops, admin));
        DeploymentCommentService service = new DeploymentCommentService(mock(DeploymentCommentRepository.class),
                mock(DeploymentRepository.class), users, mock(MailService.class), "http://localhost:5173");
        Deployment request = requestBy(developer);

        assertEquals(List.of(devops, admin), service.recipients(request, developer), "le demandeur écrit : l'équipe est prévenue");
        assertEquals(List.of(developer), service.recipients(request, devops), "un DevOps répond : le demandeur est prévenu");
    }
}
