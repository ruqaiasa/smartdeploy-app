package com.smartdeploy.environment;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** RG-O2 : un développeur crée les environnements de développement et de test de SES applications seulement. */
class EnvironmentCreationRuleTest {

    private final EnvironmentRepository environments = mock(EnvironmentRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final EnvironmentService service = new EnvironmentService(environments, applications);

    private static User developer(long id) {
        User u = new User();
        u.setId(id);
        u.setRole(Role.DEVELOPER);
        return u;
    }

    private void connect(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    private Application ownedBy(User owner) {
        Application app = new Application();
        app.setId(7L);
        app.setName("fullstack-demo");
        app.setOwner(owner);
        when(applications.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(app));
        return app;
    }

    @AfterEach
    void disconnect() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aDeveloperCannotCreateAProductionEnvironment() {
        User karim = developer(1);
        ownedBy(karim);
        connect(karim);

        AccessDeniedException refused = assertThrows(AccessDeniedException.class, () -> service.create(
                new CreateEnvironmentRequest(7L, EnvironmentType.PRODUCTION, "Prod", null, null, null)));

        assertTrue(refused.getMessage().contains("production"));
        verify(environments, never()).saveAndFlush(any());
    }

    @Test
    void aDeveloperCannotCreateAnEnvironmentForSomeoneElsesApplication() {
        ownedBy(developer(2));
        connect(developer(1));

        assertThrows(AccessDeniedException.class, () -> service.create(
                new CreateEnvironmentRequest(7L, EnvironmentType.DEVELOPMENT, "dev", null, null, null)));
        verify(environments, never()).saveAndFlush(any());
    }
}
