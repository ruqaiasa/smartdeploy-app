package com.smartdeploy.platformbackup;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PlatformBackupServiceTest {

    @TempDir
    Path dossier;

    private PlatformBackupService service(int retention) {
        return new PlatformBackupService(mock(PlatformBackupRunRepository.class), mock(ApplicationEventPublisher.class),
                true, "smartdeploy-postgres", "smartdeploy_db", "postgres", dossier.toString(), retention, "");
    }

    @Test
    void azureCopiesRotateOverSevenDays() {
        assertEquals("smartdeploy-db/smartdeploy-db-lundi.dump", PlatformBackupService.blobName(LocalDateTime.of(2026, 11, 16, 2, 30)));
        assertEquals(PlatformBackupService.blobName(LocalDateTime.of(2026, 11, 16, 2, 30)),
                PlatformBackupService.blobName(LocalDateTime.of(2026, 11, 23, 2, 30)), "le lundi suivant remplace le précédent");
    }

    @Test
    void onlyTheMostRecentLocalExportsAreKept() throws Exception {
        for (int jour = 10; jour <= 19; jour++) {
            Files.writeString(dossier.resolve("smartdeploy-db-2026-11-" + jour + "-0230.dump"), "x");
        }
        Files.writeString(dossier.resolve("autre-fichier.txt"), "à ne pas toucher");

        service(7).purgeOldFiles();

        assertEquals(8, Files.list(dossier).count(), "7 exports et le fichier étranger");
        assertTrue(Files.exists(dossier.resolve("smartdeploy-db-2026-11-19-0230.dump")));
        assertFalse(Files.exists(dossier.resolve("smartdeploy-db-2026-11-12-0230.dump")));
        assertTrue(Files.exists(dossier.resolve("autre-fichier.txt")));
    }

    @Test
    void theDumpRunsInsideTheDatabaseContainer() {
        assertEquals("docker exec smartdeploy-postgres pg_dump -U postgres -d smartdeploy_db -Fc",
                String.join(" ", service(7).command()));
    }
}
