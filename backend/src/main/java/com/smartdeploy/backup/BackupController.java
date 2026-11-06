package com.smartdeploy.backup;

import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

@RestController
public class BackupController {

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    /** Environnements proposés sur la page, avec leur présence dans le cluster. */
    @GetMapping("/api/backups/environments")
    @PreAuthorize("isAuthenticated()")
    public List<BackupEnvironment> environments() {
        return handle(backupService::environments);
    }

    /** Sauvegardes qui couvrent l'environnement (planifiées, avant déploiement, manuelles). */
    @GetMapping("/api/environments/{environmentId}/backups")
    @PreAuthorize("isAuthenticated()")
    public List<BackupInfo> list(@PathVariable Long environmentId) {
        return handle(() -> backupService.list(environmentId));
    }

    @PostMapping("/api/environments/{environmentId}/backups")
    @PreAuthorize("hasAnyRole('DEVOPS','ADMIN')")
    public Map<String, String> backupNow(@PathVariable Long environmentId, @AuthenticationPrincipal User currentUser) {
        return handle(() -> Map.of("name", backupService.backupNow(environmentId, currentUser.getEmail())));
    }

    /** Restauration de vérification (à côté) : sans risque pour l'application en service. */
    @PostMapping("/api/environments/{environmentId}/backups/{backupName}/restore-side")
    @PreAuthorize("hasAnyRole('DEVOPS','ADMIN')")
    public BackupService.RestoreResult restoreSide(@PathVariable Long environmentId, @PathVariable String backupName,
                                                   @AuthenticationPrincipal User currentUser) {
        return handle(() -> backupService.restore(environmentId, backupName, BackupService.RestoreMode.SIDE, currentUser.getEmail()));
    }

    /** Restauration en remplacement (sinistre) : l'espace de noms est supprimé puis restauré. */
    @PostMapping("/api/environments/{environmentId}/backups/{backupName}/restore-replace")
    @PreAuthorize("hasRole('ADMIN')")
    public BackupService.RestoreResult restoreReplace(@PathVariable Long environmentId, @PathVariable String backupName,
                                                      @AuthenticationPrincipal User currentUser) {
        return handle(() -> backupService.restore(environmentId, backupName, BackupService.RestoreMode.REPLACE, currentUser.getEmail()));
    }

    @GetMapping("/api/restores/{restoreName}")
    @PreAuthorize("isAuthenticated()")
    public Map<String, String> restore(@PathVariable String restoreName) {
        return handle(() -> Map.of("name", restoreName, "phase", backupService.restorePhase(restoreName)));
    }

    private static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        } catch (io.fabric8.kubernetes.client.KubernetesClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, exception.getCode() == 403
                    ? "Droits du compte de service SmartDeploy insuffisants pour piloter Velero" : "Cluster ou Velero injoignable");
        }
    }
}
