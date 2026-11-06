package com.smartdeploy.backup;

import com.smartdeploy.deployment.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Faut-il un point de restauration avant ce déploiement ? Oui si l'option est
 * active et si l'environnement existe déjà dans le cluster (un premier
 * déploiement n'a rien à protéger).
 */
@Component
public class RestorePointPolicy {

    private static final Logger log = LoggerFactory.getLogger(RestorePointPolicy.class);

    private final VeleroProperties properties;
    private final VeleroClient velero;

    public RestorePointPolicy(VeleroProperties properties, VeleroClient velero) {
        this.properties = properties;
        this.velero = velero;
    }

    public boolean required(Deployment deployment) {
        if (!properties.restorePointBeforeDeployment()) {
            return false;
        }
        try {
            return velero.namespaceExists(deployment.getEnvironment().getNamespace());
        } catch (RuntimeException exception) {
            log.warn("Point de restauration ignoré : cluster injoignable ({})", exception.getMessage());
            return false;
        }
    }
}
