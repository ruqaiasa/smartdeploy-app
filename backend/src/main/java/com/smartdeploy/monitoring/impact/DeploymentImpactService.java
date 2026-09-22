package com.smartdeploy.monitoring.impact;

import com.smartdeploy.monitoring.dto.DeploymentImpactResponse;
import com.smartdeploy.monitoring.dto.MetricPoint;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeploymentImpactService {

    private static final double DEGRADATION_THRESHOLD = 20.0;
    private static final double IMPROVEMENT_THRESHOLD = -20.0;


    public DeploymentImpactResponse evaluate(
            Long deploymentId,
            List<MetricPoint> cpuBefore,
            List<MetricPoint> cpuAfter,
            List<MetricPoint> memoryBefore,
            List<MetricPoint> memoryAfter
    ) {

        // =========================================================
        // 1. CALCULER LES MOYENNES DISPONIBLES
        // =========================================================

        double averageCpuBefore =
                average(cpuBefore);

        double averageCpuAfter =
                average(cpuAfter);

        double averageMemoryBefore =
                average(memoryBefore);

        double averageMemoryAfter =
                average(memoryAfter);


        // =========================================================
        // 2. VÉRIFIER SI LA COMPARAISON EST POSSIBLE
        // =========================================================

        boolean enoughData =
                !cpuBefore.isEmpty()
                        && !cpuAfter.isEmpty()
                        && !memoryBefore.isEmpty()
                        && !memoryAfter.isEmpty();


        // =========================================================
        // 3. DONNÉES INSUFFISANTES
        // =========================================================

        if (!enoughData) {

            return new DeploymentImpactResponse(
                    deploymentId,

                    averageCpuBefore,
                    averageCpuAfter,
                    0.0,

                    averageMemoryBefore,
                    averageMemoryAfter,
                    0.0,

                    DeploymentImpactStatus.UNKNOWN
            );
        }


        // =========================================================
        // 4. CALCULER LES VARIATIONS
        // =========================================================

        double cpuChangePercent =
                calculateChangePercent(
                        averageCpuBefore,
                        averageCpuAfter
                );

        double memoryChangePercent =
                calculateChangePercent(
                        averageMemoryBefore,
                        averageMemoryAfter
                );


        // =========================================================
        // 5. ÉVALUER L'IMPACT DU DÉPLOIEMENT
        // =========================================================

        DeploymentImpactStatus status =
                determineStatus(
                        cpuChangePercent,
                        memoryChangePercent
                );


        // =========================================================
        // 6. RETOUR
        // =========================================================

        return new DeploymentImpactResponse(
                deploymentId,

                averageCpuBefore,
                averageCpuAfter,
                cpuChangePercent,

                averageMemoryBefore,
                averageMemoryAfter,
                memoryChangePercent,

                status
        );
    }


    // =============================================================
    // CALCUL DE LA MOYENNE
    // =============================================================

    private double average(List<MetricPoint> points) {

        return points.stream()
                .mapToDouble(MetricPoint::value)
                .average()
                .orElse(0.0);
    }


    // =============================================================
    // CALCUL DU POURCENTAGE DE VARIATION
    // =============================================================

    private double calculateChangePercent(
            double before,
            double after
    ) {

        if (before == 0.0) {
            return 0.0;
        }

        return ((after - before) / before) * 100;
    }


    // =============================================================
    // DÉTERMINATION DU STATUT
    // =============================================================

    private DeploymentImpactStatus determineStatus(
            double cpuChangePercent,
            double memoryChangePercent
    ) {

        // ---------------------------------------------------------
        // DEGRADED
        // ---------------------------------------------------------
        // Si le CPU OU la mémoire augmente fortement après
        // le déploiement, on considère que les performances
        // se sont dégradées.
        // ---------------------------------------------------------

        if (cpuChangePercent >= DEGRADATION_THRESHOLD
                || memoryChangePercent >= DEGRADATION_THRESHOLD) {

            return DeploymentImpactStatus.DEGRADED;
        }


        // ---------------------------------------------------------
        // IMPROVED
        // ---------------------------------------------------------
        // Si le CPU OU la mémoire diminue fortement,
        // sans augmentation importante de l'autre métrique,
        // le déploiement est considéré comme amélioré.
        // ---------------------------------------------------------

        boolean cpuImproved =
                cpuChangePercent <= IMPROVEMENT_THRESHOLD;

        boolean memoryImproved =
                memoryChangePercent <= IMPROVEMENT_THRESHOLD;

        if (cpuImproved || memoryImproved) {

            return DeploymentImpactStatus.IMPROVED;
        }


        // ---------------------------------------------------------
        // STABLE
        // ---------------------------------------------------------
        // Pas de changement suffisamment important pour considérer
        // le déploiement comme amélioré ou dégradé.
        // ---------------------------------------------------------

        return DeploymentImpactStatus.STABLE;
    }
}