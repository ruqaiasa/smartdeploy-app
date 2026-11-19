package com.smartdeploy.backup;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Sauvegardes et restaurations des environnements, par Velero.
 * - les sauvegardes d'un environnement incluent les sauvegardes planifiées qui le couvrent ;
 * - « restaurer à côté » recrée l'application dans un espace de noms de vérification ;
 * - « restaurer en remplacement » supprime l'espace de noms, puis le restaure (sinistre).
 */
@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    static final String ACTOR = "Sauvegardes (Velero)";

    public enum RestoreMode { SIDE, REPLACE }

    public record RestoreResult(String restoreName, String targetNamespace) {
    }

    private final VeleroClient velero;
    private final VeleroProperties properties;
    private final EnvironmentRepository environmentRepository;
    private final BackupTasks tasks;
    private final ApplicationEventPublisher eventPublisher;
    private com.smartdeploy.deployment.DeploymentRepository deploymentRepository;

    /** Facultatif : vérifie qu'aucun déploiement n'est en cours avant une restauration. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDeploymentRepository(com.smartdeploy.deployment.DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    public BackupService(VeleroClient velero, VeleroProperties properties, EnvironmentRepository environmentRepository,
                         BackupTasks tasks, ApplicationEventPublisher eventPublisher) {
        this.velero = velero;
        this.properties = properties;
        this.environmentRepository = environmentRepository;
        this.tasks = tasks;
        this.eventPublisher = eventPublisher;
    }

    public List<BackupInfo> list(Long environmentId) {
        Environment environment = environment(environmentId);
        return velero.backups().stream()
                .filter(backup -> BackupNames.covers(backup, environment.getNamespace()))
                .map(BackupNames::toInfo)
                .sorted(Comparator.comparing((BackupInfo b) -> b.startedAt() == null ? "~" : b.startedAt()).reversed())
                .toList();
    }

    /**
     * Environnements actifs, en indiquant ceux qui existent réellement dans le
     * cluster (les seuls à pouvoir être sauvegardés). Les environnements
     * déployés viennent en premier.
     */
    public List<BackupEnvironment> environments() {
        return environmentRepository.findAll().stream()
                .filter(environment -> environment.isActive() && !environment.isDeleted())
                .map(environment -> new BackupEnvironment(environment.getId(), applicationName(environment),
                        environment.getName(), environment.getNamespace(), deployed(environment.getNamespace())))
                .sorted(Comparator.comparing((BackupEnvironment e) -> !e.deployed())
                        .thenComparing(e -> e.applicationName() == null ? "" : e.applicationName())
                        .thenComparing(BackupEnvironment::environmentName))
                .toList();
    }

    private boolean deployed(String namespace) {
        try {
            return namespace != null && !namespace.isBlank() && velero.namespaceExists(namespace);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String backupNow(Long environmentId, String requestedBy) {
        Environment environment = environment(environmentId);
        if (!deployed(environment.getNamespace())) {
            throw new IllegalStateException("L'environnement n'est pas déployé : il n'y a rien à sauvegarder");
        }
        String name = BackupNames.name("manuelle", environment.getName(), LocalDateTime.now());
        velero.createBackup(name, environment.getNamespace(), properties.manualBackupTtl(), Map.of(
                BackupNames.LABEL_REASON, "manual",
                BackupNames.LABEL_ENVIRONMENT, String.valueOf(environmentId)));
        audit(AuditAction.BACKUP_REQUESTED, requestedBy, environment,
                "Sauvegarde « " + name + " » demandée pour " + environment.getNamespace(), null);
        tasks.watchBackup(name, environment.getId(), environment.getName(), applicationName(environment));
        return name;
    }

    public RestoreResult restore(Long environmentId, String backupName, RestoreMode mode, String requestedBy) {
        Environment environment = environment(environmentId);
        var backup = velero.backup(backupName).orElseThrow(() -> new NoSuchElementException("Sauvegarde introuvable : " + backupName));
        if (!BackupNames.covers(backup, environment.getNamespace())) {
            throw new IllegalArgumentException("Cette sauvegarde ne contient pas l'environnement " + environment.getNamespace());
        }
        if (!"Completed".equals(VeleroClient.phase(backup))) {
            throw new IllegalStateException("Seule une sauvegarde terminée avec succès peut être restaurée");
        }
        // Une restauration en remplacement supprime l'espace de noms : jamais pendant un déploiement.
        if (mode == RestoreMode.REPLACE && deploymentRepository != null
                && deploymentRepository.existsByEnvironmentAndStatusInAndDeletedFalse(environment, List.of(
                        com.smartdeploy.deployment.DeploymentStatus.PENDING,
                        com.smartdeploy.deployment.DeploymentStatus.RUNNING,
                        com.smartdeploy.deployment.DeploymentStatus.AWAITING_APPROVAL))) {
            throw new IllegalStateException(
                    "Un déploiement est en attente, en cours ou à approuver dans cet environnement : terminez-le ou annulez-le avant de restaurer");
        }
        LocalDateTime now = LocalDateTime.now();
        String target = mode == RestoreMode.SIDE
                ? BackupNames.name(environment.getNamespace(), "verif", now)
                : environment.getNamespace();
        String restoreName = BackupNames.name("restauration", environment.getName(), now);
        audit(AuditAction.RESTORE_STARTED, requestedBy, environment,
                (mode == RestoreMode.SIDE ? "Restauration de vérification" : "Restauration en remplacement")
                        + " de « " + backupName + " » vers " + target,
                mode == RestoreMode.REPLACE ? "L'espace de noms " + target + " est supprimé puis restauré." : null);
        BackupInfo info = BackupNames.toInfo(backup);
        tasks.runRestore(restoreName, backupName, environment.getNamespace(), target, mode == RestoreMode.REPLACE,
                environment.getId(), environment.getName(), applicationName(environment),
                BackupProtection.localTime(info.startedAt()), info.deploymentId());
        return new RestoreResult(restoreName, target);
    }

    /** Points de restauration pris avant un déploiement, avec le déploiement qu'ils protègent. */
    public List<BackupInfo> restorePoints() {
        return velero.backups().stream()
                .map(BackupNames::toInfo)
                .filter(info -> "PRE_DEPLOYMENT".equals(info.origin()) && info.deploymentId() != null)
                .toList();
    }

    /** État de protection de chaque environnement déployé : âge de sa dernière sauvegarde réussie. */
    public List<BackupProtection.EnvironmentProtection> protection() {
        var backups = velero.backups();                       // un seul appel à Kubernetes
        java.time.Instant now = java.time.Instant.now();
        return environments().stream()
                .filter(BackupEnvironment::deployed)
                .map(env -> BackupProtection.evaluate(env, backups.stream()
                        .filter(backup -> BackupNames.covers(backup, env.namespace()))
                        .map(BackupNames::toInfo)
                        .toList(), now))
                .toList();
    }

    public String restorePhase(String restoreName) {
        return velero.restore(restoreName).map(VeleroClient::phase).orElse("Inconnue");
    }

    private Environment environment(Long environmentId) {
        return environmentRepository.findById(environmentId)
                .orElseThrow(() -> new NoSuchElementException("Environnement introuvable : " + environmentId));
    }

    static String applicationName(Environment environment) {
        return environment.getApplication() == null ? null : environment.getApplication().getName();
    }

    private void audit(AuditAction action, String actor, Environment environment, String summary, String details) {
        eventPublisher.publishEvent(new AuditEvent(action, actor == null ? ACTOR : actor, "ENVIRONMENT", environment.getId(),
                applicationName(environment), environment.getName(), summary, details, false));
        log.info("Velero : {}", summary);
    }
}
