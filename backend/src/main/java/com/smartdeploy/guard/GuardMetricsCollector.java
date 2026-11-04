package com.smartdeploy.guard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.monitoring.PrometheusClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Lit dans Prometheus les métriques nécessaires au gardien.
 * Renvoie null si Prometheus est injoignable : l'évaluation sera « inconnue ».
 */
@Component
public class GuardMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(GuardMetricsCollector.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PrometheusClient prometheusClient;

    public GuardMetricsCollector(PrometheusClient prometheusClient) {
        this.prometheusClient = prometheusClient;
    }

    public GuardSnapshot collect(String namespace, String prefix, long secondsSinceStart, int baselineMinutes) {
        try {
            String cpu = GuardQueries.cpuPercent(namespace, prefix);
            String memory = GuardQueries.memoryMb(namespace, prefix);
            return new GuardSnapshot(
                    intOrNull(scalar(GuardQueries.desiredPods(namespace, prefix))),
                    intOrNull(scalar(GuardQueries.unavailablePods(namespace, prefix))),
                    intOrNull(scalar(GuardQueries.restartsSince(namespace, prefix, secondsSinceStart))),
                    labels(GuardQueries.blockedContainers(namespace, prefix),
                            metric -> metric.path("pod").asText() + " : " + metric.path("reason").asText()),
                    labels(GuardQueries.oomKilled(namespace, prefix), metric -> metric.path("pod").asText()),
                    scalar(GuardQueries.memoryLimitRatio(namespace, prefix)),
                    scalar(cpu),
                    scalar(memory),
                    scalar(GuardQueries.baseline(cpu, secondsSinceStart, baselineMinutes)),
                    scalar(GuardQueries.baseline(memory, secondsSinceStart, baselineMinutes))
            );
        } catch (RuntimeException exception) {
            log.warn("Gardien : lecture Prometheus impossible pour {} / {} : {}", namespace, prefix, exception.getMessage());
            return null;
        }
    }

    /** Première valeur du résultat, ou null si la série est absente (non mesurable). */
    private Double scalar(String query) {
        JsonNode results = results(query);
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        JsonNode value = results.get(0).path("value");
        if (!value.isArray() || value.size() < 2) {
            return null;
        }
        double parsed = Double.parseDouble(value.get(1).asText());
        return Double.isNaN(parsed) || Double.isInfinite(parsed) ? null : parsed;
    }

    private List<String> labels(String query, Function<JsonNode, String> format) {
        List<String> values = new ArrayList<>();
        for (JsonNode result : results(query)) {
            values.add(format.apply(result.path("metric")));
        }
        return values.stream().distinct().sorted().toList();
    }

    private JsonNode results(String query) {
        try {
            return MAPPER.readTree(prometheusClient.query(query)).path("data").path("result");
        } catch (Exception exception) {
            throw new IllegalStateException("Réponse Prometheus illisible", exception);
        }
    }

    private static Integer intOrNull(Double value) {
        return value == null ? null : (int) Math.round(value);
    }
}
