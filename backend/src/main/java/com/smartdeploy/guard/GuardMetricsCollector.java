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
        Reader reader = new Reader(namespace, prefix);
        String cpu = GuardQueries.cpuPercent(namespace, prefix);
        String memory = GuardQueries.memoryMb(namespace, prefix);

        GuardSnapshot snapshot = new GuardSnapshot(
                intOrNull(reader.scalar("pods attendus", GuardQueries.desiredPods(namespace, prefix))),
                intOrNull(reader.scalar("pods indisponibles", GuardQueries.unavailablePods(namespace, prefix))),
                intOrNull(reader.scalar("redémarrages", GuardQueries.restartsSince(namespace, prefix, secondsSinceStart))),
                reader.labels("conteneurs bloqués", GuardQueries.blockedContainers(namespace, prefix),
                        metric -> metric.path("pod").asText() + " : " + metric.path("reason").asText()),
                reader.labels("arrêts OOMKilled", GuardQueries.oomKilled(namespace, prefix), metric -> metric.path("pod").asText()),
                reader.scalar("mémoire / limite", GuardQueries.memoryLimitRatio(namespace, prefix)),
                reader.scalar("CPU", cpu),
                reader.scalar("mémoire", memory),
                reader.scalar("CPU de référence", GuardQueries.baseline(cpu, secondsSinceStart, baselineMinutes)),
                reader.scalar("mémoire de référence", GuardQueries.baseline(memory, secondsSinceStart, baselineMinutes))
        );

        // Toutes les requêtes ont échoué : Prometheus est réellement injoignable.
        if (reader.failures == reader.queries) {
            log.warn("Gardien : Prometheus injoignable pour {} / {} ({})", namespace, prefix, reader.lastError);
            return null;
        }
        return snapshot;
    }

    /**
     * Exécute chaque requête isolément : une requête en erreur rend seulement
     * son contrôle « non mesuré », sans invalider les autres mesures.
     */
    private final class Reader {
        private final String namespace;
        private final String prefix;
        private int queries;
        private int failures;
        private String lastError;

        private Reader(String namespace, String prefix) {
            this.namespace = namespace;
            this.prefix = prefix;
        }

        Double scalar(String name, String query) {
            JsonNode results = run(name, query);
            if (results == null || !results.isArray() || results.isEmpty()) {
                return null;
            }
            JsonNode value = results.get(0).path("value");
            return value.isArray() && value.size() >= 2 ? parse(value.get(1).asText()) : null;
        }

        List<String> labels(String name, String query, Function<JsonNode, String> format) {
            JsonNode results = run(name, query);
            if (results == null || !results.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (JsonNode result : results) {
                values.add(format.apply(result.path("metric")));
            }
            return values.stream().distinct().sorted().toList();
        }

        private JsonNode run(String name, String query) {
            queries++;
            try {
                return MAPPER.readTree(prometheusClient.query(query)).path("data").path("result");
            } catch (Exception exception) {
                failures++;
                lastError = exception.getMessage();
                log.warn("Gardien : requête « {} » en erreur pour {} / {} : {}", name, namespace, prefix, exception.getMessage());
                return null;
            }
        }
    }

    /** Valeur Prometheus, ou null si non mesurable (NaN, +Inf, -Inf, texte invalide). */
    static Double parse(String text) {
        try {
            double value = Double.parseDouble(text);
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer intOrNull(Double value) {
        return value == null ? null : (int) Math.round(value);
    }
}
