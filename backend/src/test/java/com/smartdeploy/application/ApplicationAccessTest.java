package com.smartdeploy.application;

import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

/** RG-O1 : un développeur n'accède qu'à ses applications ; DevOps, administrateur et lecteur voient tout. */
class ApplicationAccessTest {

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setRole(role);
        return u;
    }

    private static Application ownedBy(User owner) {
        Application app = new Application();
        app.setName("fullstack-demo");
        app.setOwner(owner);
        return app;
    }

    @Test
    void aDeveloperOnlyReachesHisOwnApplications() {
        User karim = user(1, Role.DEVELOPER);
        User sara = user(2, Role.DEVELOPER);

        assertDoesNotThrow(() -> ApplicationAccess.ensureCanAccess(ownedBy(karim), karim));
        AccessDeniedException refused = assertThrows(AccessDeniedException.class,
                () -> ApplicationAccess.ensureCanAccess(ownedBy(sara), karim));
        assertTrue(refused.getMessage().contains("ne vous appartient pas"));
        assertThrows(AccessDeniedException.class, () -> ApplicationAccess.ensureCanAccess(ownedBy(null), karim),
                "une application sans propriétaire n'est visible que de l'équipe DevOps");
    }

    @Test
    void theTeamAndTheSystemSeeEverything() {
        Application someoneElses = ownedBy(user(2, Role.DEVELOPER));
        assertDoesNotThrow(() -> ApplicationAccess.ensureCanAccess(someoneElses, user(3, Role.DEVOPS)));
        assertDoesNotThrow(() -> ApplicationAccess.ensureCanAccess(someoneElses, user(4, Role.ADMIN)));
        assertDoesNotThrow(() -> ApplicationAccess.ensureCanAccess(someoneElses, user(5, Role.VIEWER)));
        assertDoesNotThrow(() -> ApplicationAccess.ensureCanAccess(someoneElses, null), "tâches planifiées, retours de Jenkins");
        assertFalse(ApplicationAccess.isRestricted(user(3, Role.DEVOPS)));
        assertTrue(ApplicationAccess.isRestricted(user(1, Role.DEVELOPER)));
    }
}
