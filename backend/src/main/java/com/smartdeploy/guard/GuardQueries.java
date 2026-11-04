package com.smartdeploy.guard;

/**
 * Requêtes PromQL du gardien. Une application est identifiée par son
 * espace de noms et son préfixe (« fullstack-demo » couvre le backend,
 * le frontend et le pod de base « postgresql-N » généré par SmartDeploy).
 */
public final class GuardQueries {

    static final String BLOCKING_REASONS =
            "CrashLoopBackOff|ImagePullBackOff|ErrImagePull|CreateContainerConfigError|CreateContainerError|InvalidImageName";

    private GuardQueries() {
    }

    /** « fullstack-demo-backend » → « fullstack-demo ». */
    public static String applicationPrefix(String kubernetesDeploymentName) {
        String prefix = kubernetesDeploymentName;
        for (String suffix : new String[] {"-backend", "-frontend"}) {
            if (prefix.endsWith(suffix)) {
                return prefix.substring(0, prefix.length() - suffix.length());
            }
        }
        return prefix;
    }

    static String podPattern(String prefix) {
        return "(" + prefix + "-.*|postgresql-[0-9]+)";
    }

    static String deploymentPattern(String prefix) {
        return prefix + "(-backend|-frontend)?";
    }

    static String desiredPods(String ns, String prefix) {
        return "sum(kube_deployment_spec_replicas{namespace=\"%s\",deployment=~\"%s\"})"
                .formatted(ns, deploymentPattern(prefix));
    }

    static String unavailablePods(String ns, String prefix) {
        return ("sum(kube_deployment_status_replicas_unavailable{namespace=\"%s\",deployment=~\"%s\"})"
                + " + sum(kube_statefulset_replicas{namespace=\"%s\"} - kube_statefulset_status_replicas_ready{namespace=\"%s\"} or vector(0))")
                .formatted(ns, deploymentPattern(prefix), ns, ns);
    }

    static String restartsSince(String ns, String prefix, long seconds) {
        return "round(sum(increase(kube_pod_container_status_restarts_total{namespace=\"%s\",pod=~\"%s\"}[%ds])))"
                .formatted(ns, podPattern(prefix), Math.max(60, seconds));
    }

    static String blockedContainers(String ns, String prefix) {
        return "kube_pod_container_status_waiting_reason{namespace=\"%s\",pod=~\"%s\",reason=~\"%s\"} == 1"
                .formatted(ns, podPattern(prefix), BLOCKING_REASONS);
    }

    static String oomKilled(String ns, String prefix) {
        return "kube_pod_container_status_last_terminated_reason{namespace=\"%s\",pod=~\"%s\",reason=\"OOMKilled\"} == 1"
                .formatted(ns, podPattern(prefix));
    }

    /**
     * Les deux côtés sont d'abord regroupés par conteneur : cAdvisor publie
     * souvent plusieurs séries pour un même conteneur (ancienne et nouvelle
     * instance), ce qui ferait échouer l'association (« many-to-one matching
     * must be explicit »).
     */
    static String memoryLimitRatio(String ns, String prefix) {
        String selector = "namespace=\"%s\",pod=~\"%s\"".formatted(ns, podPattern(prefix));
        return ("max(max by (namespace,pod,container) (container_memory_working_set_bytes{%s,container!=\"\",container!=\"POD\"})"
                + " / on(namespace,pod,container) max by (namespace,pod,container) (kube_pod_container_resource_limits{%s,resource=\"memory\"}))")
                .formatted(selector, selector);
    }

    static String cpuPercent(String ns, String prefix) {
        return "sum(rate(container_cpu_usage_seconds_total{namespace=\"%s\",pod=~\"%s\",container!=\"\",container!=\"POD\"}[2m])) * 100"
                .formatted(ns, podPattern(prefix));
    }

    static String memoryMb(String ns, String prefix) {
        return "sum(container_memory_working_set_bytes{namespace=\"%s\",pod=~\"%s\",container!=\"\",container!=\"POD\"}) / 1024 / 1024"
                .formatted(ns, podPattern(prefix));
    }

    /**
     * Moyenne sur les 15 minutes qui précèdent le déploiement : la requête
     * est décalée (offset) du temps écoulé depuis la mise en service, plus
     * 2 minutes pour exclure le remplacement des pods lui-même.
     */
    static String baseline(String query, long secondsSinceStart, int baselineMinutes) {
        return "avg_over_time((%s)[%dm:1m] offset %ds)".formatted(query, baselineMinutes, secondsSinceStart + 120);
    }
}
