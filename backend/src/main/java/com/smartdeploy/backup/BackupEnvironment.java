package com.smartdeploy.backup;

/** Un environnement proposé sur la page Sauvegardes, avec son état dans le cluster. */
public record BackupEnvironment(
        Long environmentId,
        String applicationName,
        String environmentName,
        String namespace,
        boolean deployed
) {
}
