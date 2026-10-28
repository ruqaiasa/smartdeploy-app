package com.smartdeploy.integration.kubernetes;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.IntOrString;
import java.util.LinkedHashMap;
import java.util.Objects;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import java.util.ArrayList;
import java.util.List;
import io.fabric8.kubernetes.client.KubernetesClient;
@org.springframework.stereotype.Service
public class KubernetesManifestGeneratorService {

    private final KubernetesClient kubernetesClient;

    public KubernetesManifestGeneratorService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Génère un Deployment Kubernetes.
     */
    public String generateDeployment(
            KubernetesDeploymentConfigRequest request
    ) {

        String appName = request.applicationName();

        Map<String, String> labels = Map.of(
                "app", appName
        );

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(appName)
                .withNamespace(request.namespace())
                .addToLabels(labels)
                .endMetadata()

                .withNewSpec()
                .withReplicas(request.replicas())

                .withNewSelector()
                .addToMatchLabels(labels)
                .endSelector()

                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(labels)
                .endMetadata()

                .withNewSpec()
                .addNewContainer()
                .withName(appName)
                .withImage(request.image())

                .addNewPort()
                .withContainerPort(request.containerPort())
                .endPort()

// STARTUP PROBE
                .withNewStartupProbe()
                .withNewTcpSocket()
                .withNewPort(request.containerPort())
                .endTcpSocket()
                .withPeriodSeconds(10)
                .withTimeoutSeconds(3)
                .withFailureThreshold(30)
                .endStartupProbe()
// READINESS PROBE
                .withNewReadinessProbe()
                .withNewTcpSocket()
                .withNewPort(request.containerPort())
                .endTcpSocket()
                .withPeriodSeconds(10)
                .withTimeoutSeconds(3)
                .withFailureThreshold(3)
                .endReadinessProbe()

// LIVENESS PROBE
                .withNewLivenessProbe()
                .withNewTcpSocket()
                .withNewPort(request.containerPort())
                .endTcpSocket()
                .withPeriodSeconds(20)
                .withTimeoutSeconds(3)
                .withFailureThreshold(3)
                .endLivenessProbe()

                .endContainer()
                .endSpec()
                .endTemplate()

                .endSpec()
                .build();

        return Serialization.asYaml(deployment);
    }

    /**
     * Génère un Service Kubernetes interne (ClusterIP).
     */
    public String generateService(
            KubernetesDeploymentConfigRequest request
    ) {

        String appName = request.applicationName();

        Map<String, String> labels = Map.of(
                "app", appName
        );

        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName(appName + "-service")
                .withNamespace(request.namespace())
                .addToLabels(labels)
                .endMetadata()

                .withNewSpec()
                .withType("ClusterIP")

                .addToSelector(labels)

                .addNewPort()
                .withPort(request.containerPort())
                .withNewTargetPort(
                        request.containerPort()
                )
                .endPort()

                .endSpec()
                .build();

        return Serialization.asYaml(service);
    }
    public KubernetesFullStackPreviewResponse generateFullStack(
            KubernetesFullStackRequest request
    ) {
        KubernetesComponentRequest frontend = request.frontend();
        KubernetesComponentRequest backend = request.backend();

        if (frontend.name().equals(backend.name())) {
            throw new IllegalArgumentException(
                    "Le frontend et le backend doivent avoir des noms différents"
            );
        }

        KubernetesDeploymentConfigRequest frontendConfig =
                new KubernetesDeploymentConfigRequest(
                        frontend.name(),
                        request.namespace(),
                        frontend.image(),
                        frontend.replicas(),
                        frontend.containerPort()
                );

        KubernetesDeploymentConfigRequest backendConfig =
                new KubernetesDeploymentConfigRequest(
                        backend.name(),
                        request.namespace(),
                        backend.image(),
                        backend.replicas(),
                        backend.containerPort()
                );

        return new KubernetesFullStackPreviewResponse(
                generateDeployment(frontendConfig),
                generateService(frontendConfig),
                generateDeployment(backendConfig),
                generateService(backendConfig)
        );
    }
    public KubernetesDatabasePreviewResponse generateDatabase(
            String namespace,
            KubernetesDatabaseRequest request
    ) {
        if (request.mode() == KubernetesDatabaseRequest.DatabaseMode.EXTERNAL) {
            return new KubernetesDatabasePreviewResponse(null, null);
        }

        String name = "postgresql";
        int port = request.port();

        Map<String, String> labels = Map.of("app", name);

        StatefulSet statefulSet = new StatefulSetBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace(namespace)
                .addToLabels(labels)
                .endMetadata()

                .withNewSpec()
                .withServiceName(name)
                .withReplicas(1)

                .withNewSelector()
                .addToMatchLabels(labels)
                .endSelector()

                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(labels)
                .endMetadata()

                .withNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage("postgres:16")
                .withImagePullPolicy("IfNotPresent")

                // PostgreSQL port
                .addNewPort()
                .withContainerPort(port)
                .withName("postgresql")
                .endPort()

                // Database name
                .addNewEnv()
                .withName("POSTGRES_DB")
                .withValue(request.databaseName())
                .endEnv()

                // Database username
                .addNewEnv()
                .withName("POSTGRES_USER")
                .withValue(request.username())
                .endEnv()

                // Database password from Kubernetes Secret
                .addNewEnv()
                .withName("POSTGRES_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(request.passwordSecretName())
                .withKey(request.passwordSecretKey())
                .endSecretKeyRef()
                .endValueFrom()
                .endEnv()

                // PostgreSQL persistent data directory
                .addNewEnv()
                .withName("PGDATA")
                .withValue("/var/lib/postgresql/data/pgdata")
                .endEnv()

                // Persistent volume
                .addNewVolumeMount()
                .withName("postgres-data")
                .withMountPath("/var/lib/postgresql/data")
                .endVolumeMount()

                // Readiness probe
                .withNewReadinessProbe()
                .withNewExec()
                .addToCommand(
                        "sh",
                        "-c",
                        "pg_isready -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\""
                )
                .endExec()
                .withInitialDelaySeconds(10)
                .withPeriodSeconds(10)
                .withTimeoutSeconds(5)
                .withFailureThreshold(3)
                .endReadinessProbe()

                // Liveness probe
                .withNewLivenessProbe()
                .withNewExec()
                .addToCommand(
                        "sh",
                        "-c",
                        "pg_isready -U \"$POSTGRES_USER\" -d \"$POSTGRES_DB\""
                )
                .endExec()
                .withInitialDelaySeconds(30)
                .withPeriodSeconds(20)
                .withTimeoutSeconds(5)
                .withFailureThreshold(3)
                .endLivenessProbe()

                // Resource limits
                .withNewResources()
                .addToRequests(
                        "cpu",
                        new Quantity("100m")
                )
                .addToRequests(
                        "memory",
                        new Quantity("256Mi")
                )
                .addToLimits(
                        "cpu",
                        new Quantity("500m")
                )
                .addToLimits(
                        "memory",
                        new Quantity("512Mi")
                )
                .endResources()

                .endContainer()
                .endSpec()
                .endTemplate()

                // PersistentVolumeClaim
                .addNewVolumeClaimTemplate()
                .withNewMetadata()
                .withName("postgres-data")
                .endMetadata()

                .withNewSpec()
                .addToAccessModes("ReadWriteOnce")

                .withNewResources()
                .addToRequests(
                        "storage",
                        new Quantity(request.storageGi() + "Gi")
                )
                .endResources()

                .endSpec()
                .endVolumeClaimTemplate()

                .endSpec()
                .build();
        io.fabric8.kubernetes.api.model.Service service =
                new ServiceBuilder()
                        .withNewMetadata()
                        .withName(name)
                        .withNamespace(namespace)
                        .endMetadata()
                        .withNewSpec()
                        .withClusterIP("None")
                        .addToSelector(labels)
                        .addNewPort()
                        .withPort(port)
                        .withNewTargetPort(port)
                        .endPort()
                        .endSpec()
                        .build();

        return new KubernetesDatabasePreviewResponse(
                Serialization.asYaml(statefulSet),
                Serialization.asYaml(service)
        );
    }
    public KubernetesApplicationPreviewResponse generateApplication(
            KubernetesApplicationRequest request
    ) {

        if (request.frontend() == null && request.backend() == null) {
            throw new IllegalArgumentException(
                    "Une application doit contenir au moins un frontend ou un backend"
            );
        }

        if (request.frontend() != null
                && request.backend() != null
                && request.frontend().name().equals(request.backend().name())) {

            throw new IllegalArgumentException(
                    "Le frontend et le backend doivent avoir des noms différents"
            );
        }

        if (request.database() != null && request.backend() == null) {
            throw new IllegalArgumentException(
                    "Une base de données nécessite un backend"
            );
        }

        validateDatabaseSecret(request.namespace(), request.database());
        Map<String, String> manifests = new LinkedHashMap<>();

        // FRONTEND
        if (request.frontend() != null) {

            KubernetesComponentRequest frontend = request.frontend();

            KubernetesDeploymentConfigRequest config =
                    new KubernetesDeploymentConfigRequest(
                            frontend.name(),
                            request.namespace(),
                            frontend.image(),
                            frontend.replicas(),
                            frontend.containerPort()
                    );

            manifests.put(
                    "frontend-deployment.yaml",
                    generateDeployment(config)
            );

            manifests.put(
                    "frontend-service.yaml",
                    generateService(config)
            );
        }

        // BACKEND
        if (request.backend() != null) {

            KubernetesComponentRequest backend = request.backend();

            KubernetesDeploymentConfigRequest config =
                    new KubernetesDeploymentConfigRequest(
                            backend.name(),
                            request.namespace(),
                            backend.image(),
                            backend.replicas(),
                            backend.containerPort()
                    );

            manifests.put(
                    "backend-deployment.yaml",
                    request.database() != null
                            ? generateBackendWithDatabase(config, request.database())
                            : generateDeployment(config)
            );

            manifests.put(
                    "backend-service.yaml",
                    generateService(config)
            );
        }

        // DATABASE
        if (request.database() != null
                && request.database().mode()
                == KubernetesDatabaseRequest.DatabaseMode.MANAGED) {

            KubernetesDatabasePreviewResponse database =
                    generateDatabase(request.namespace(), request.database());

            manifests.put(
                    "postgresql-statefulset.yaml",
                    database.statefulSetYaml()
            );

            manifests.put(
                    "postgresql-service.yaml",
                    database.serviceYaml()
            );
        }

        // INGRESS
        if (request.ingress() != null) {

            manifests.put(
                    "application-ingress.yaml",
                    generateIngress(request)
            );
        }

        return new KubernetesApplicationPreviewResponse(manifests);
    }
    public String generateBackendWithDatabase(
            KubernetesDeploymentConfigRequest config,
            KubernetesDatabaseRequest database
    ) {
        Deployment deployment = Serialization.unmarshal(
                generateDeployment(config),
                Deployment.class
        );

        if (database == null) {
            return Serialization.asYaml(deployment);
        }

        if (database.mode() == null) {
            throw new IllegalArgumentException("Database mode is required");
        }

        String host;

        if (database.mode() == KubernetesDatabaseRequest.DatabaseMode.MANAGED) {
            host = "postgresql";
        } else {
            host = database.externalHost();

            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException(
                        "External PostgreSQL host is required"
                );
            }
        }

        List<EnvVar> env = new ArrayList<>();

        env.add(new EnvVarBuilder()
                .withName("SPRING_DATASOURCE_URL")
                .withValue(
                        "jdbc:postgresql://" + host + ":" +
                                database.port() + "/" + database.databaseName()
                )
                .build());

        env.add(new EnvVarBuilder()
                .withName("SPRING_DATASOURCE_USERNAME")
                .withValue(database.username())
                .build());

        env.add(new EnvVarBuilder()
                .withName("SPRING_DATASOURCE_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(database.passwordSecretName())
                .withKey(database.passwordSecretKey())
                .endSecretKeyRef()
                .endValueFrom()
                .build());

        deployment.getSpec()
                .getTemplate()
                .getSpec()
                .getContainers()
                .get(0)
                .setEnv(env);

        return Serialization.asYaml(deployment);
    }
    public String generateIngress(KubernetesApplicationRequest request) {

        if (request.ingress() == null) {
            return null;
        }

        if (request.frontend() == null && request.backend() == null) {
            throw new IllegalArgumentException(
                    "Un frontend ou un backend est obligatoire"
            );
        }

        KubernetesIngressRequest config = request.ingress();

        List<HTTPIngressPath> paths = new ArrayList<>();

        // 1. Route /api uniquement si Frontend ET Backend existent
        if (request.frontend() != null && request.backend() != null) {

            paths.add(new HTTPIngressPathBuilder()
                    .withPath("/api")
                    .withPathType("Prefix")
                    .withNewBackend()
                    .withNewService()
                    .withName(request.backend().name() + "-service")
                    .withNewPort()
                    .withNumber(request.backend().containerPort())
                    .endPort()
                    .endService()
                    .endBackend()
                    .build());
        }

        // 2. Route principale /
        // Frontend s'il existe, sinon Backend
        KubernetesComponentRequest rootTarget =
                request.frontend() != null
                        ? request.frontend()
                        : request.backend();

        paths.add(new HTTPIngressPathBuilder()
                .withPath("/")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName(rootTarget.name() + "-service")
                .withNewPort()
                .withNumber(rootTarget.containerPort())
                .endPort()
                .endService()
                .endBackend()
                .build());

        // 3. Génération Ingress
        IngressBuilder builder = new IngressBuilder()
                .withNewMetadata()
                .withName(rootTarget.name() + "-ingress")
                .withNamespace(request.namespace())
                .endMetadata()
                .withNewSpec()
                .withIngressClassName(
                        config.ingressClassName() == null
                                || config.ingressClassName().isBlank()
                                ? "traefik"
                                : config.ingressClassName()
                )
                .addNewRule()
                .withHost(config.host())
                .withNewHttp()
                .withPaths(paths)
                .endHttp()
                .endRule()
                .endSpec();

        // 4. TLS optionnel
        if (config.tlsSecretName() != null
                && !config.tlsSecretName().isBlank()) {

            builder.editSpec()
                    .addNewTl()
                    .addToHosts(config.host())
                    .withSecretName(config.tlsSecretName())
                    .endTl()
                    .endSpec();
        }

        return Serialization.asYaml(builder.build());
    }
    private void validateDatabaseSecret(
            String namespace,
            KubernetesDatabaseRequest database
    ) {
        if (database == null) {
            return;
        }

        var secret = kubernetesClient.secrets()
                .inNamespace(namespace)
                .withName(database.passwordSecretName())
                .get();

        if (secret == null) {
            throw new IllegalArgumentException(
                    "Le Secret Kubernetes '" +
                            database.passwordSecretName() +
                            "' n'existe pas dans le namespace '" +
                            namespace + "'"
            );
        }

        if (secret.getData() == null ||
                !secret.getData().containsKey(database.passwordSecretKey())) {
            throw new IllegalArgumentException(
                    "La clé '" + database.passwordSecretKey() +
                            "' est absente du Secret '" +
                            database.passwordSecretName() + "'"
            );
        }
    }
}