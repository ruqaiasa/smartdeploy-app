package com.smartdeploy.monitoring;

import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentNotFoundException;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.monitoring.dto.ApplicationMetricsHistoryResponse;
import com.smartdeploy.monitoring.dto.ApplicationMetricsResponse;
import com.smartdeploy.monitoring.dto.DeploymentImpactResponse;
import com.smartdeploy.monitoring.dto.MetricPoint;
import com.smartdeploy.monitoring.health.HealthEvaluationService;
import com.smartdeploy.monitoring.health.HealthStatus;
import com.smartdeploy.monitoring.impact.DeploymentImpactService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import com.smartdeploy.alert.AlertDetectionService;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentNotFoundException;
import com.smartdeploy.environment.EnvironmentRepository;
@Service
public class MonitoringService {

    private final PrometheusClient prometheusClient;
    private final HealthEvaluationService healthEvaluationService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentImpactService deploymentImpactService;

    private final AlertDetectionService alertDetectionService;
    private final EnvironmentRepository environmentRepository;

    public MonitoringService(
            PrometheusClient prometheusClient,
            HealthEvaluationService healthEvaluationService,
            DeploymentRepository deploymentRepository,
            DeploymentImpactService deploymentImpactService,
            AlertDetectionService alertDetectionService,
            EnvironmentRepository environmentRepository
    ) {
        this.prometheusClient = prometheusClient;
        this.healthEvaluationService = healthEvaluationService;
        this.deploymentRepository = deploymentRepository;
        this.deploymentImpactService = deploymentImpactService;
        this.alertDetectionService = alertDetectionService;
        this.environmentRepository = environmentRepository;
    }
    public String getTargetsStatus() {
        return prometheusClient.query("up");
    }
    public ApplicationMetricsResponse getApplicationMetrics(
            String namespace,
            String applicationName
    ) {

        String cpuQuery = """
                sum(rate(container_cpu_usage_seconds_total{
                  namespace="%s",
                  pod=~"%s-.*",
                  container="%s"
                }[2m])) * 100
                """.formatted(
                namespace,
                applicationName,
                applicationName
        );

        String memoryQuery = """
                sum(container_memory_working_set_bytes{
                  namespace="%s",
                  pod=~"%s-.*",
                  container="%s"
                }) / 1024 / 1024
                """.formatted(
                namespace,
                applicationName,
                applicationName
        );

        String runningPodsQuery = """
                sum(kube_deployment_status_replicas_available{
                  namespace="%s",
                  deployment="%s"
                })
                """.formatted(
                namespace,
                applicationName
        );

        String desiredPodsQuery = """
                sum(kube_deployment_spec_replicas{
                  namespace="%s",
                  deployment="%s"
                })
                """.formatted(
                namespace,
                applicationName
        );

        String restartCountQuery = """
                sum(kube_pod_container_status_restarts_total{
                  namespace="%s",
                  container="%s"
                })
                """.formatted(
                namespace,
                applicationName
        );

        double cpuUsage =
                prometheusClient.querySingleValue(cpuQuery);

        double memoryUsage =
                prometheusClient.querySingleValue(memoryQuery);

        int runningPods =
                (int) prometheusClient.querySingleValue(runningPodsQuery);

        int desiredPods =
                (int) prometheusClient.querySingleValue(desiredPodsQuery);

        int restartCount =
                (int) prometheusClient.querySingleValue(restartCountQuery);

        HealthStatus healthStatus =
                healthEvaluationService.evaluate(
                        cpuUsage,
                        memoryUsage,
                        runningPods,
                        desiredPods,
                        restartCount
                );

        return new ApplicationMetricsResponse(
                cpuUsage,
                memoryUsage,
                runningPods,
                desiredPods,
                restartCount,
                healthStatus
        );
    }

    public ApplicationMetricsHistoryResponse getApplicationMetricsHistory(
            String namespace,
            String applicationName,
            long start,
            long end,
            int step
    ) {

        String cpuQuery = """
                sum(rate(container_cpu_usage_seconds_total{
                  namespace="%s",
                  pod=~"%s-.*",
                  container="%s"
                }[2m])) * 100
                """.formatted(
                namespace,
                applicationName,
                applicationName
        );

        String memoryQuery = """
                sum(container_memory_working_set_bytes{
                  namespace="%s",
                  pod=~"%s-.*",
                  container="%s"
                }) / 1024 / 1024
                """.formatted(
                namespace,
                applicationName,
                applicationName
        );

        List<MetricPoint> cpu =
                prometheusClient.queryRangePoints(
                        cpuQuery,
                        start,
                        end,
                        step
                );

        List<MetricPoint> memory =
                prometheusClient.queryRangePoints(
                        memoryQuery,
                        start,
                        end,
                        step
                );

        return new ApplicationMetricsHistoryResponse(
                cpu,
                memory
        );
    }

    public DeploymentImpactResponse getDeploymentImpact(Long deploymentId) {

        Deployment deployment = deploymentRepository
                .findByIdAndDeletedFalse(deploymentId)
                .orElseThrow(
                        () -> new DeploymentNotFoundException(deploymentId)
                );

        String namespace =
                deployment.getEnvironment().getNamespace();

        /*
         * TEMPORAIRE :
         * Le nom métier de l'application dans SmartDeploy est "Spring sec",
         * alors que le nom technique Kubernetes est "smartdeploy-demo".
         *
         * Plus tard, ce nom devra être stocké dynamiquement dans SmartDeploy
         * lors du déploiement Jenkins/Kubernetes.
         */
        String kubernetesApplicationName = "smartdeploy-demo";

        /*
         * IMPORTANT :
         * finishedAt est enregistré en base comme LocalDateTime,
         * donc sans information de fuseau horaire.
         *
         * Dans notre environnement, cette valeur représente l'heure
         * locale tunisienne.
         *
         * On associe donc explicitement Africa/Tunis avant de convertir
         * vers le timestamp Unix utilisé par Prometheus.
         */
        ZoneId tunisZone = ZoneId.of("Africa/Tunis");

        long deploymentTime = deployment
                .getFinishedAt()
                .atZone(tunisZone)
                .toEpochSecond();

        /*
         * Fenêtre de comparaison :
         *
         * 5 minutes BEFORE
         *       ↓
         *    DEPLOYMENT
         *       ↓
         * 5 minutes AFTER
         */
        long window = 5 * 60;

        long beforeStart = deploymentTime - window;
        long beforeEnd = deploymentTime;

        long afterStart = deploymentTime;
        long afterEnd = deploymentTime + window;

        int step = 15;

        String cpuQuery = """
                sum(rate(container_cpu_usage_seconds_total{
                  namespace="%s",
                  pod=~"%s-.*",
                  container="%s"
                }[2m])) * 100
                """.formatted(
                namespace,
                kubernetesApplicationName,
                kubernetesApplicationName
        );

        String memoryQuery = """
                sum(container_memory_working_set_bytes{
                  namespace="%s",
                  pod=~"%s-.*",
                  container="%s"
                }) / 1024 / 1024
                """.formatted(
                namespace,
                kubernetesApplicationName,
                kubernetesApplicationName
        );

        List<MetricPoint> cpuBefore =
                prometheusClient.queryRangePoints(
                        cpuQuery,
                        beforeStart,
                        beforeEnd,
                        step
                );

        List<MetricPoint> cpuAfter =
                prometheusClient.queryRangePoints(
                        cpuQuery,
                        afterStart,
                        afterEnd,
                        step
                );

        List<MetricPoint> memoryBefore =
                prometheusClient.queryRangePoints(
                        memoryQuery,
                        beforeStart,
                        beforeEnd,
                        step
                );

        List<MetricPoint> memoryAfter =
                prometheusClient.queryRangePoints(
                        memoryQuery,
                        afterStart,
                        afterEnd,
                        step
                );

        /*
         * DEBUG TEMPORAIRE
         */
        System.out.println("=== DEPLOYMENT IMPACT DEBUG ===");

        System.out.println(
                "Deployment ID : " + deploymentId
        );

        System.out.println(
                "Kubernetes application : "
                        + kubernetesApplicationName
        );

        System.out.println(
                "Namespace : " + namespace
        );

        System.out.println(
                "Deployment time : " + deploymentTime
        );

        System.out.println(
                "Before start : " + beforeStart
        );

        System.out.println(
                "Before end   : " + beforeEnd
        );

        System.out.println(
                "After start  : " + afterStart
        );

        System.out.println(
                "After end    : " + afterEnd
        );

        System.out.println(
                "Deployment date : "
                        + Instant.ofEpochSecond(deploymentTime)
        );

        System.out.println(
                "Before start date : "
                        + Instant.ofEpochSecond(beforeStart)
        );

        System.out.println(
                "After end date : "
                        + Instant.ofEpochSecond(afterEnd)
        );

        System.out.println(
                "CPU BEFORE points : "
                        + cpuBefore.size()
        );

        System.out.println(
                "CPU AFTER points : "
                        + cpuAfter.size()
        );

        System.out.println(
                "MEMORY BEFORE points : "
                        + memoryBefore.size()
        );

        System.out.println(
                "MEMORY AFTER points : "
                        + memoryAfter.size()
        );

        return deploymentImpactService.evaluate(
                deploymentId,
                cpuBefore,
                cpuAfter,
                memoryBefore,
                memoryAfter
        );
    }

    public ApplicationMetricsResponse getApplicationMetricsAndEvaluateAlerts(
            Long environmentId,
            String kubernetesApplicationName
    ) {

        Environment environment = environmentRepository
                .findByIdAndDeletedFalse(environmentId)
                .orElseThrow(
                        () -> new EnvironmentNotFoundException(environmentId)
                );

        ApplicationMetricsResponse metrics =
                getApplicationMetrics(
                        environment.getNamespace(),
                        kubernetesApplicationName
                );

        alertDetectionService.evaluateApplicationHealth(
                environment.getApplication(),
                environment,
                null,
                metrics.runningPods(),
                metrics.desiredPods(),
                metrics.restartCount()
        );

        return metrics;
    }
}