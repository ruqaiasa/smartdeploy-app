package com.smartdeploy.version;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

/**
 * Mise en cohérence, une seule fois (RG-V2) : les versions déjà mises en service avec succès
 * avant l'introduction des versions figées sont figées sur leur déploiement réussi le plus ancien.
 * Idempotent : une version déjà figée n'est jamais modifiée.
 */
@Component
public class VersionPinningBackfill {

    private static final Logger log = LoggerFactory.getLogger(VersionPinningBackfill.class);

    private final VersionRepository versionRepository;
    private final DeploymentRepository deploymentRepository;

    public VersionPinningBackfill(VersionRepository versionRepository, DeploymentRepository deploymentRepository) {
        this.versionRepository = versionRepository;
        this.deploymentRepository = deploymentRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void pinAlreadyBuiltVersions() {
        int pinned = 0;
        for (Version version : versionRepository.findAll()) {
            if (version.isDeleted() || version.getPinnedAt() != null || version.getId() == null) {
                continue;
            }
            Optional<Deployment> firstSuccess = deploymentRepository.findByVersion_IdAndDeletedFalse(version.getId()).stream()
                    .filter(d -> d.getStatus() == DeploymentStatus.SUCCESS || d.getStatus() == DeploymentStatus.ROLLED_BACK)
                    .filter(d -> d.getDeployedBackendImage() != null && d.getDeployedBackendImage().contains("@sha256:"))
                    .min(Comparator.comparing(Deployment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
            if (firstSuccess.isEmpty()) {
                continue;
            }
            Deployment success = firstSuccess.get();
            if (version.getGitCommit() == null && success.getBuiltCommit() != null) {
                version.setGitCommit(success.getBuiltCommit());
            }
            version.setPinnedAt(success.getFinishedAt() != null ? success.getFinishedAt() : LocalDateTime.now());
            pinned++;
        }
        if (pinned > 0) {
            log.info("Versions figées : {} version(s) déjà mises en service ont été figées (mise en cohérence unique)", pinned);
        }
    }
}
