package com.smartdeploy.application;

import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * RG-O1 : un développeur ne voit et ne manipule que ses applications (et leurs versions,
 * environnements et déploiements). Le DevOps, l'administrateur et le lecteur voient tout.
 * Les traitements sans utilisateur connecté (tâches planifiées, retours de Jenkins) ne sont pas restreints.
 */
@Component
public class ApplicationAccess {

    /** L'utilisateur connecté, ou null pour un traitement du système. */
    public User currentUser() {
        return authenticatedUser();
    }

    /** Même chose, utilisable sans injection (contrôleurs existants). */
    public static User authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof User user ? user : null;
    }

    /** RG-O1 appliqué à l'utilisateur connecté. */
    public static void ensureCurrentUserCanAccess(Application application) {
        ensureCanAccess(application, authenticatedUser());
    }

    public static boolean isRestricted(User user) {
        return user != null && user.getRole() == Role.DEVELOPER;
    }

    /** Propriétaire à imposer dans une recherche : l'utilisateur s'il est restreint, sinon aucun. */
    public Long ownerFilter() {
        User user = currentUser();
        return isRestricted(user) ? user.getId() : null;
    }

    public void ensureCanAccess(Application application) {
        ensureCanAccess(application, currentUser());
    }

    public static void ensureCanAccess(Application application, User user) {
        if (!isRestricted(user)) {
            return;
        }
        User owner = application.getOwner();
        if (owner == null || !Objects.equals(owner.getId(), user.getId())) {
            throw new AccessDeniedException("Cette application ne vous appartient pas : demandez à son propriétaire ou à un administrateur");
        }
    }
}
