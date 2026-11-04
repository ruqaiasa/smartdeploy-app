package com.smartdeploy.guard;

import com.smartdeploy.monitoring.PrometheusClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduit l'incident du déploiement témoin : une seule requête rejetée par
 * Prometheus invalidait toutes les mesures (« Prometheus injoignable »).
 */
class GuardMetricsCollectorTest {

    private static String value(String v) {
        return "{\"status\":\"success\",\"data\":{\"result\":[{\"metric\":{},\"value\":[1730000000,\"" + v + "\"]}]}}";
    }

    @Test
    void shouldKeepOtherMeasuresWhenOneQueryIsRejected() {
        PrometheusClient client = mock(PrometheusClient.class);
        when(client.query(anyString())).thenAnswer(invocation -> {
            String query = invocation.getArgument(0);
            if (query.contains("kube_pod_container_resource_limits")) {
                throw new IllegalStateException("422 : many-to-one matching must be explicit");
            }
            return value("3");
        });

        GuardSnapshot snapshot = new GuardMetricsCollector(client).collect("sd-dev-fullstack-demo", "fullstack-demo", 120, 15);

        assertNotNull(snapshot, "une requête en erreur ne doit pas invalider les autres");
        assertNull(snapshot.memoryLimitRatio(), "seul le contrôle concerné devient non mesuré");
        assertEquals(3, snapshot.desiredPods());
        assertEquals(3.0, snapshot.cpuPercent());
    }

    @Test
    void shouldReportUnreachableOnlyWhenEveryQueryFails() {
        PrometheusClient client = mock(PrometheusClient.class);
        when(client.query(anyString())).thenThrow(new IllegalStateException("Connection refused"));

        assertNull(new GuardMetricsCollector(client).collect("ns", "app", 120, 15));
    }

    @Test
    void shouldTreatInfiniteAndNotANumberAsNotMeasurable() {
        assertNull(GuardMetricsCollector.parse("+Inf"));
        assertNull(GuardMetricsCollector.parse("-Inf"));
        assertNull(GuardMetricsCollector.parse("NaN"));
        assertEquals(0.42, GuardMetricsCollector.parse("0.42"));
    }
}
