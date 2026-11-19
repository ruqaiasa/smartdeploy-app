package com.smartdeploy.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** « Pas de mesure » ne doit jamais être confondu avec « zéro » (fausse alerte d'indisponibilité). */
class PrometheusOptionalValueTest {

    @Test
    void anAbsentSeriesIsNotZero() {
        assertTrue(PrometheusClient.firstValue("{\"status\":\"success\",\"data\":{\"result\":[]}}").isEmpty());
    }

    @Test
    void aMeasuredZeroStaysZero() {
        var value = PrometheusClient.firstValue("{\"data\":{\"result\":[{\"metric\":{},\"value\":[1731.5,\"0\"]}]}}");
        assertTrue(value.isPresent());
        assertEquals(0.0, value.getAsDouble());
    }

    @Test
    void aValueIsRead() {
        assertEquals(2.0, PrometheusClient.firstValue("{\"data\":{\"result\":[{\"value\":[1731.5,\"2\"]}]}}").getAsDouble());
    }
}
