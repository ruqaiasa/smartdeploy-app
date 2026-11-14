package com.smartdeploy.infrastructure;

import com.smartdeploy.audit.AuditAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RaidEvaluatorTest {

    private static final Instant NOW = Instant.parse("2026-11-14T22:21:23Z");

    @Test
    void shouldClassifyEachRaidState() {
        assertEquals(RaidStatus.HEALTHY, RaidEvaluator.evaluate("md0", 2, 2, 0, false, NOW).status());
        assertEquals(RaidStatus.DEGRADED, RaidEvaluator.evaluate("md0", 2, 1, 1, false, NOW).status());
        assertEquals(RaidStatus.DEGRADED, RaidEvaluator.evaluate("md0", 2, 1, 0, false, NOW).status(),
                "un disque retiré (--remove) n'est plus « failed », mais il manque toujours");
        assertEquals(RaidStatus.RECOVERING, RaidEvaluator.evaluate("md0", 2, 1, 0, true, NOW).status());
        assertEquals(RaidStatus.UNKNOWN, RaidEvaluator.evaluate("md0", null, null, null, false, NOW).status(),
                "pas de mesure n'est pas une panne");
    }

    @Test
    void shouldPublishOneEventPerStateChange() {
        // Le scénario réel du test : sain → panne → reconstruction → sain.
        assertNull(RaidEvaluator.transition(null, RaidStatus.HEALTHY), "un premier état sain ne notifie rien");
        assertEquals(AuditAction.RAID_DEGRADED, RaidEvaluator.transition(RaidStatus.HEALTHY, RaidStatus.DEGRADED));
        assertNull(RaidEvaluator.transition(RaidStatus.DEGRADED, RaidStatus.DEGRADED), "pas de doublon toutes les 30 s");
        assertEquals(AuditAction.RAID_RECOVERING, RaidEvaluator.transition(RaidStatus.DEGRADED, RaidStatus.RECOVERING));
        assertEquals(AuditAction.RAID_RESTORED, RaidEvaluator.transition(RaidStatus.RECOVERING, RaidStatus.HEALTHY));
        assertNull(RaidEvaluator.transition(RaidStatus.HEALTHY, RaidStatus.UNKNOWN), "Prometheus muet : aucune fausse alerte");
        assertEquals(AuditAction.RAID_DEGRADED, RaidEvaluator.transition(null, RaidStatus.DEGRADED),
                "un RAID déjà dégradé au démarrage est signalé");
    }

    @Test
    void shouldReadPrometheusSeriesByDevice() {
        // Format réel de la réponse de Prometheus, relevé sur la VM.
        String json = "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":["
                + "{\"metric\":{\"__name__\":\"node_md_disks\",\"device\":\"md0\",\"state\":\"active\"},"
                + "\"value\":[1794694941.823,\"2\"]}]}}";
        assertEquals(Map.of("md0", 2), RaidMetricsReader.parse(json));
        assertTrue(RaidMetricsReader.parse("{\"status\":\"success\",\"data\":{\"result\":[]}}").isEmpty(),
                "aucune série : aucune valeur, et surtout pas zéro");
    }
}
