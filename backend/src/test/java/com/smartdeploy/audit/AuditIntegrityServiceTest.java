package com.smartdeploy.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditIntegrityServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    /** Construit une chaîne valide de n entrées, précédée de « legacy » entrées sans empreinte. */
    private static List<AuditLog> chain(int legacy, int n) {
        List<AuditLog> entries = new ArrayList<>();
        long id = 1;
        for (int i = 0; i < legacy; i++) {
            entries.add(AuditLog.builder().id(id++).action(AuditAction.LOGIN_SUCCESS).actor("a").summary("ancien")
                    .occurredAt(LocalDateTime.of(2026, 11, 1, 10, 0)).build());
        }
        String previous = AuditChain.GENESIS;
        for (int i = 0; i < n; i++) {
            AuditLog entry = AuditLog.builder().id(id++).action(AuditAction.DEPLOYMENT_CREATED)
                    .actor("ruqaia@smartdeploy.com").summary("Déploiement #" + i + " lancé")
                    .ipAddress("192.168.85.1").occurredAt(LocalDateTime.of(2026, 11, 3, 21, i, 0, 123_456_000))
                    .previousHash(previous).build();
            entry.setHash(AuditChain.hash(entry));
            previous = entry.getHash();
            entries.add(entry);
        }
        return entries;
    }

    private AuditIntegrityReport verify(List<AuditLog> entries) {
        when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(entries));
        return new AuditIntegrityService(auditLogRepository).verify();
    }

    @Test
    void shouldValidateIntactChainAndCountLegacyEntries() {
        AuditIntegrityReport report = verify(chain(2, 5));

        assertTrue(report.valid());
        assertEquals(5, report.checkedEntries());
        assertEquals(2, report.unchainedEntries());
        assertNull(report.firstInvalidId());
    }

    @Test
    void shouldDetectModifiedEntry() {
        List<AuditLog> entries = chain(0, 5);
        entries.get(2).setSummary("Déploiement falsifié");

        AuditIntegrityReport report = verify(entries);

        assertFalse(report.valid());
        assertEquals(3L, report.firstInvalidId());
        assertTrue(report.message().contains("modifié"));
    }

    @Test
    void shouldDetectDeletedEntry() {
        List<AuditLog> entries = chain(0, 5);
        entries.remove(2);

        AuditIntegrityReport report = verify(entries);

        assertFalse(report.valid());
        assertEquals(4L, report.firstInvalidId());
        assertTrue(report.message().contains("supprimée"));
    }
}
