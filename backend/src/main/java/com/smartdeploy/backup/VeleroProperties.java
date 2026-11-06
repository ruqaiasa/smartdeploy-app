package com.smartdeploy.backup;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Réglages de l'intégration Velero (application.yml, section smartdeploy.velero). */
@Component
public class VeleroProperties {

    private final boolean enabled;
    private final String namespace;
    private final boolean restorePointBeforeDeployment;
    private final int restorePointTimeoutSeconds;
    private final String restorePointTtl;
    private final String manualBackupTtl;

    public VeleroProperties(
            @Value("${smartdeploy.velero.enabled:true}") boolean enabled,
            @Value("${smartdeploy.velero.namespace:velero}") String namespace,
            @Value("${smartdeploy.velero.restore-point-before-deployment:true}") boolean restorePointBeforeDeployment,
            @Value("${smartdeploy.velero.restore-point-timeout-seconds:600}") int restorePointTimeoutSeconds,
            @Value("${smartdeploy.velero.restore-point-ttl:72h0m0s}") String restorePointTtl,
            @Value("${smartdeploy.velero.manual-backup-ttl:168h0m0s}") String manualBackupTtl
    ) {
        this.enabled = enabled;
        this.namespace = namespace;
        this.restorePointBeforeDeployment = restorePointBeforeDeployment;
        this.restorePointTimeoutSeconds = restorePointTimeoutSeconds;
        this.restorePointTtl = restorePointTtl;
        this.manualBackupTtl = manualBackupTtl;
    }

    public boolean enabled() { return enabled; }
    public String namespace() { return namespace; }
    public boolean restorePointBeforeDeployment() { return enabled && restorePointBeforeDeployment; }
    public int restorePointTimeoutSeconds() { return restorePointTimeoutSeconds; }
    public String restorePointTtl() { return restorePointTtl; }
    public String manualBackupTtl() { return manualBackupTtl; }
}
