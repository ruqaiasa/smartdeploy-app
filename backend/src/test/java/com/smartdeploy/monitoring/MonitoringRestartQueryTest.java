package com.smartdeploy.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonitoringRestartQueryTest {

    @Test
    void patternShouldCoverBackendFrontendAndDatabase() {
        assertEquals(
                "(fullstack-demo-.*|postgresql-[0-9]+)",
                MonitoringService.restartPodPattern("fullstack-demo-backend")
        );
        assertEquals(
                "(shop-.*|postgresql-[0-9]+)",
                MonitoringService.restartPodPattern("shop-frontend")
        );
    }

    @Test
    void patternShouldKeepNameWithoutKnownSuffix() {
        assertEquals(
                "(spring-sec-.*|postgresql-[0-9]+)",
                MonitoringService.restartPodPattern("spring-sec")
        );
    }

    @Test
    void queryShouldCountRecentRestartsOnly() {
        String query = MonitoringService.restartCountQuery(
                "sd-dev-fullstack-demo",
                "fullstack-demo-backend"
        );

        assertTrue(query.contains("increase("));
        assertTrue(query.contains("[15m]"));
        assertTrue(query.contains("namespace=\"sd-dev-fullstack-demo\""));
        assertTrue(query.contains("pod=~\"(fullstack-demo-.*|postgresql-[0-9]+)\""));
    }
}
