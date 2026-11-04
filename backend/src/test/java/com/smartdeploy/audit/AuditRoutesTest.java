package com.smartdeploy.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuditRoutesTest {

    @Test
    void shouldDescribeResourceOperationsInFrench() {
        assertEquals("Application créée", AuditRoutes.describe("POST", "/api/applications").summary());
        assertEquals("Environnement #4 désactivé", AuditRoutes.describe("PATCH", "/api/environments/4/deactivate").summary());
        assertEquals("Version #9 supprimée", AuditRoutes.describe("DELETE", "/api/versions/9").summary());
        assertEquals(AuditAction.RESOURCE_DELETED, AuditRoutes.describe("DELETE", "/api/versions/9").action());
        assertEquals(9L, AuditRoutes.describe("DELETE", "/api/versions/9").targetId());
    }

    @Test
    void shouldDescribeSecurityRelevantRoutes() {
        assertEquals(AuditAction.LOGOUT, AuditRoutes.describe("POST", "/api/auth/logout").action());
        assertTrue(AuditRoutes.describe("POST", "/api/kubernetes/secrets").summary().contains("jamais journalisée"));
        assertEquals("Mot de passe modifié", AuditRoutes.describe("PUT", "/api/users/change-password").summary());
    }

    @Test
    void shouldIgnoreReadOnlyAndDomainCoveredRoutes() {
        assertTrue(AuditRoutes.isIgnored("/api/kubernetes/manifests/preview"));
        assertTrue(AuditRoutes.isIgnored("/api/auth/refresh"));
        assertTrue(AuditRoutes.isCoveredByDomainEvents("POST", "/api/deployments/5/rollback"));
        assertTrue(AuditRoutes.isCoveredByDomainEvents("PATCH", "/api/users/2/role"));
        assertFalse(AuditRoutes.isCoveredByDomainEvents("POST", "/api/applications"));
    }

    @Test
    void shouldStillTraceUnknownFutureRoutes() {
        AuditRoutes.RouteAudit route = AuditRoutes.describe("POST", "/api/backups/run");
        assertEquals(AuditAction.RESOURCE_UPDATED, route.action());
        assertEquals("Action POST /api/backups/run", route.summary());
    }
}
