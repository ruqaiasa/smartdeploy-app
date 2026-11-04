package com.smartdeploy.integration.kubernetes;

/**
 * Ressources des conteneurs générés par SmartDeploy.
 *
 * - « requests » : ce que Kubernetes réserve au conteneur (placement sur un nœud) ;
 * - « limits » : ce qu'il ne peut pas dépasser (au-delà de la limite mémoire,
 *   le conteneur est arrêté : OOMKilled).
 *
 * Valeurs dimensionnées à partir des mesures du gardien de déploiement sur
 * l'application de démonstration. Le backend JVM démarre avec
 * MaxRAMPercentage=75 : avec 1 Gi de limite, sa mémoire reste sous la limite.
 */
public enum ContainerResourceProfile {

    BACKEND("100m", "384Mi", "1000m", "1Gi"),
    FRONTEND("25m", "32Mi", "250m", "128Mi");

    private final String requestCpu;
    private final String requestMemory;
    private final String limitCpu;
    private final String limitMemory;

    ContainerResourceProfile(String requestCpu, String requestMemory, String limitCpu, String limitMemory) {
        this.requestCpu = requestCpu;
        this.requestMemory = requestMemory;
        this.limitCpu = limitCpu;
        this.limitMemory = limitMemory;
    }

    public String requestCpu() { return requestCpu; }
    public String requestMemory() { return requestMemory; }
    public String limitCpu() { return limitCpu; }
    public String limitMemory() { return limitMemory; }
}
