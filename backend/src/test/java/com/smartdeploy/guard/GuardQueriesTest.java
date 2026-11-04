package com.smartdeploy.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuardQueriesTest {

    @Test
    void shouldDeriveApplicationPrefix() {
        assertEquals("fullstack-demo", GuardQueries.applicationPrefix("fullstack-demo-backend"));
        assertEquals("fullstack-demo", GuardQueries.applicationPrefix("fullstack-demo-frontend"));
        assertEquals("api", GuardQueries.applicationPrefix("api"));
    }

    @Test
    void shouldCoverApplicationPodsAndItsDatabase() {
        String query = GuardQueries.restartsSince("ns", "fullstack-demo", 30);
        assertTrue(query.contains("pod=~\"(fullstack-demo-.*|postgresql-[0-9]+)\""));
        assertTrue(query.contains("[60s]"), "fenêtre minimale d'une minute");
    }

    @Test
    void shouldShiftBaselineBeforeTheRollout() {
        String query = GuardQueries.baseline("sum(x)", 300, 15);
        assertEquals("avg_over_time((sum(x))[15m:1m] offset 420s)", query);
    }
}
