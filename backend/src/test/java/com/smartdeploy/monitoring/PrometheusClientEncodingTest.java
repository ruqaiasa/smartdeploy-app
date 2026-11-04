package com.smartdeploy.monitoring;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prometheus décode les paramètres comme un formulaire : « + » devient un
 * espace. La requête encodée doit donc revenir intacte après ce décodage.
 */
class PrometheusClientEncodingTest {

    @Test
    void shouldSurviveFormDecodingOnPrometheusSide() {
        String query = "sum(kube_deployment_status_replicas_unavailable{namespace=\"ns\",deployment=~\"app(-backend)?\"})"
                + " + sum(kube_statefulset_replicas{namespace=\"ns\"} - kube_statefulset_status_replicas_ready{namespace=\"ns\"} or vector(0))";

        String encoded = PrometheusClient.encode(query);

        assertFalse(encoded.contains("+"), "un « + » brut serait décodé en espace");
        assertEquals(query, URLDecoder.decode(encoded, StandardCharsets.UTF_8));
    }
}
