package com.smartdeploy.notification;

import com.smartdeploy.application.Application;
import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.mail.MailService;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import com.smartdeploy.user.UserRepository;
import com.smartdeploy.version.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApprovalMailNotifierTest {

    private UserRepository users;
    private MailService mail;
    private ApprovalMailNotifier notifier;
    private Deployment deployment;
    private User requester;

    private static User user(long id, String email, Role role, boolean enabled) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setEnabled(enabled);
        return user;
    }

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        mail = mock(MailService.class);
        notifier = new ApprovalMailNotifier(mock(DeploymentRepository.class), users, mail, "http://localhost:5173/");
        requester = user(3L, "ruqaia@smartdeploy.com", Role.ADMIN, true);
        Application application = mock(Application.class);
        when(application.getName()).thenReturn("fullstack-demo");
        Version version = mock(Version.class);
        when(version.getApplication()).thenReturn(application);
        when(version.getVersion()).thenReturn("1.1.0");
        Environment environment = mock(Environment.class);
        when(environment.getName()).thenReturn("Prod");
        deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn(135L);
        when(deployment.getRequestedBy()).thenReturn(requester);
        when(deployment.getVersion()).thenReturn(version);
        when(deployment.getEnvironment()).thenReturn(environment);
    }

    @Test
    void aRequestNotifiesEveryActiveApproverExceptTheRequester() {
        when(users.findAllByDeletedFalse()).thenReturn(List.of(
                requester,
                user(4L, "approbateur@smartdeploy.com", Role.DEVOPS, true),
                user(5L, "admin2@smartdeploy.com", Role.ADMIN, true),
                user(6L, "dev@smartdeploy.com", Role.DEVELOPER, true),
                user(7L, "ancien@smartdeploy.com", Role.DEVOPS, false)));

        notifier.notify(AuditAction.DEPLOYMENT_APPROVAL_REQUESTED, deployment);

        verify(mail).send(eq("approbateur@smartdeploy.com"), contains("approbation demandée"), contains("http://localhost:5173/deployments"));
        verify(mail).send(eq("admin2@smartdeploy.com"), anyString(), anyString());
        verify(mail, never()).send(eq("ruqaia@smartdeploy.com"), anyString(), anyString());
        verify(mail, never()).send(eq("dev@smartdeploy.com"), anyString(), anyString());
        verify(mail, never()).send(eq("ancien@smartdeploy.com"), anyString(), anyString());
    }

    @Test
    void theDecisionIsSentToTheRequesterWithTheReason() {
        when(deployment.getApprovedBy()).thenReturn("approbateur@smartdeploy.com");
        when(deployment.getApprovalComment()).thenReturn("Version jamais validée en test");

        notifier.notify(AuditAction.DEPLOYMENT_REJECTED, deployment);

        verify(mail).send(eq("ruqaia@smartdeploy.com"), contains("refusé"), contains("Version jamais validée en test"));
        verifyNoMoreInteractions(mail);
    }
}
