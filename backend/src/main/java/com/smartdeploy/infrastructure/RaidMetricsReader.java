package com.smartdeploy.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdeploy.monitoring.PrometheusClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lit l'état des RAID logiciels dans Prometheus. Les métriques viennent de
 * node_exporter, qui lit /proc/mdstat (commun à toute la machine).
 * Contrairement à querySingleValue, une série absente reste absente (null) :
 * « pas de mesure » n'est jamais confondu avec zéro.
 */
@Component
public class RaidMetricsReader {

    static final String REQUIRED = "node_md_disks_required";
    static final String ACTIVE = "node_md_disks{state=\"active\"}";
    static final String FAILED = "node_md_disks{state=\"failed\"}";
    static final String RECOVERING = "max by (device) (node_md_state{state=~\"recovering|resync\"})";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final PrometheusClient prometheusClient;

    public RaidMetricsReader(PrometheusClient prometheusClient) {
        this.prometheusClient = prometheusClient;
    }

    public List<RaidSnapshot> read(Instant now) {
        Map<String, Integer> required = byDevice(REQUIRED);
        Map<String, Integer> active = byDevice(ACTIVE);
        Map<String, Integer> failed = byDevice(FAILED);
        Map<String, Integer> recovering = byDevice(RECOVERING);
        List<RaidSnapshot> snapshots = new ArrayList<>();
        required.keySet().stream().sorted().forEach(device -> snapshots.add(RaidEvaluator.evaluate(
                device, required.get(device), active.get(device), failed.get(device),
                recovering.getOrDefault(device, 0) > 0, now)));
        return snapshots;
    }

    /** Valeur par RAID (étiquette device) ; plusieurs séries d'un même RAID → la plus grande. */
    Map<String, Integer> byDevice(String promQl) {
        return parse(prometheusClient.query(promQl));
    }

    static Map<String, Integer> parse(String json) {
        Map<String, Integer> values = new HashMap<>();
        try {
            JsonNode result = MAPPER.readTree(json).path("data").path("result");
            for (JsonNode series : result) {
                String device = series.path("metric").path("device").asText("");
                if (device.isBlank() || !series.path("value").isArray()) {
                    continue;
                }
                int value = (int) Math.round(Double.parseDouble(series.path("value").get(1).asText("0")));
                values.merge(device, value, Math::max);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Réponse de Prometheus illisible : " + exception.getMessage(), exception);
        }
        return values;
    }
}
