package com.smartdeploy.platformbackup;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Sauvegarde de la base de SmartDeploy elle-même : historique, utilisateurs et journal infalsifiable.
 * Velero protège les applications du cluster, mais cette base vit sur le poste, hors du cluster.
 * Chaque nuit : export pg_dump depuis le conteneur Docker, conservé localement (7 fichiers), puis,
 * si une adresse SAS est configurée, envoyé dans un conteneur Azure (un fichier par jour de la semaine,
 * remplacé la semaine suivante : rotation sur 7 jours sans suppression à gérer).
 */
@Service
public class PlatformBackupService {

    private static final Logger log = LoggerFactory.getLogger(PlatformBackupService.class);
    static final String ACTOR = "Sauvegarde de la plateforme";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    public record Status(boolean enabled, boolean uploadConfigured, String directory, int retention,
                         PlatformBackupRun lastRun, PlatformBackupRun lastSuccess) {
    }

    private final PlatformBackupRunRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean enabled;
    private final String container;
    private final String database;
    private final String user;
    private final Path directory;
    private final int retention;
    private final String sasUrl;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PlatformBackupService(PlatformBackupRunRepository repository, ApplicationEventPublisher eventPublisher,
                                 @Value("${smartdeploy.platform-backup.enabled:true}") boolean enabled,
                                 @Value("${smartdeploy.platform-backup.container:smartdeploy-postgres}") String container,
                                 @Value("${smartdeploy.platform-backup.database:smartdeploy_db}") String database,
                                 @Value("${spring.datasource.username:postgres}") String user,
                                 @Value("${smartdeploy.platform-backup.directory:${user.home}/smartdeploy-sauvegardes}") String directory,
                                 @Value("${smartdeploy.platform-backup.retention:7}") int retention,
                                 @Value("${smartdeploy.platform-backup.azure-container-sas-url:}") String sasUrl) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
        this.container = container;
        this.database = database;
        this.user = user;
        this.directory = Path.of(directory);
        this.retention = Math.max(1, retention);
        this.sasUrl = sasUrl == null ? "" : sasUrl.trim();
    }

    @Scheduled(cron = "${smartdeploy.platform-backup.cron:0 30 2 * * *}")
    public void nightly() {
        if (enabled) {
            run("Planification (chaque nuit)");
        }
    }

    /** Lance une sauvegarde ; une seule à la fois. */
    public PlatformBackupRun run(String triggeredBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Une sauvegarde de la base est déjà en cours");
        }
        LocalDateTime start = LocalDateTime.now();
        PlatformBackupRun run = PlatformBackupRun.builder().startedAt(start).triggeredBy(triggeredBy).build();
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve("smartdeploy-db-" + FILE_DATE.format(start) + ".dump");
            dump(file);
            long size = Files.size(file);
            if (size == 0) {
                throw new IOException("export vide");
            }
            run.setLocalFile(file.toString());
            run.setSizeBytes(size);
            String where = "localement (" + file.getFileName() + ")";
            if (!sasUrl.isEmpty()) {
                upload(file, blobName(start));
                run.setUploaded(true);
                where += " et sur Azure (" + blobName(start) + ")";
            }
            purgeOldFiles();
            run.setSuccess(true);
            run.setMessage("Base sauvegardée " + where + ", " + human(size));
            audit(AuditAction.PLATFORM_BACKUP_SUCCEEDED, triggeredBy, run.getMessage(), null);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            run.setSuccess(false);
            run.setMessage("Échec : " + exception.getMessage());
            log.warn("Sauvegarde de la base de SmartDeploy impossible : {}", exception.getMessage());
            audit(AuditAction.PLATFORM_BACKUP_FAILED, triggeredBy, "Sauvegarde de la base de SmartDeploy impossible",
                    exception.getMessage());
        } finally {
            run.setFinishedAt(LocalDateTime.now());
            running.set(false);
        }
        return repository.save(run);
    }

    /** pg_dump exécuté dans le conteneur PostgreSQL : aucun outil à installer sur le poste. */
    void dump(Path file) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command())
                .redirectOutput(file.toFile())
                .redirectErrorStream(false)
                .start();
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        String errors = new String(process.getErrorStream().readAllBytes()).trim();
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("pg_dump n'a pas terminé en 10 minutes");
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(file);
            throw new IOException("pg_dump a échoué (code " + process.exitValue() + ")"
                    + (errors.isEmpty() ? "" : " : " + errors.lines().findFirst().orElse("")));
        }
    }

    List<String> command() {
        return List.of("docker", "exec", container, "pg_dump", "-U", user, "-d", database, "-Fc");
    }

    /** Un fichier par jour de la semaine : la copie de lundi remplace celle du lundi précédent. */
    static String blobName(LocalDateTime when) {
        String day = when.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH);
        return "smartdeploy-db/smartdeploy-db-" + day + ".dump";
    }

    /** Envoi par l'API REST d'Azure (« Put Blob »), avec une adresse SAS limitée à ce conteneur. */
    void upload(Path file, String blob) throws IOException, InterruptedException {
        int q = sasUrl.indexOf('?');
        if (q < 0) {
            throw new IOException("adresse SAS invalide (jeton manquant)");
        }
        URI uri = URI.create(sasUrl.substring(0, q).replaceAll("/+$", "") + "/" + blob + sasUrl.substring(q));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMinutes(10))
                .header("x-ms-blob-type", "BlockBlob")
                .PUT(HttpRequest.BodyPublishers.ofFile(file))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("envoi vers Azure refusé (HTTP " + response.statusCode() + ")");
        }
    }

    /** Ne garde que les N exports locaux les plus récents. */
    void purgeOldFiles() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> dumps = files.filter(p -> p.getFileName().toString().matches("smartdeploy-db-.*\\.dump"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            for (Path old : dumps.subList(Math.min(retention, dumps.size()), dumps.size())) {
                Files.deleteIfExists(old);
            }
        }
    }

    public Status status() {
        return new Status(enabled, !sasUrl.isEmpty(), directory.toString(), retention,
                repository.findFirstByOrderByStartedAtDesc().orElse(null),
                repository.findFirstBySuccessTrueOrderByStartedAtDesc().orElse(null));
    }

    static String human(long bytes) {
        if (bytes < 1024) return bytes + " o";
        if (bytes < 1024 * 1024) return String.format(Locale.FRENCH, "%.1f Ko", bytes / 1024.0);
        return String.format(Locale.FRENCH, "%.1f Mo", bytes / (1024.0 * 1024));
    }

    private void audit(AuditAction action, String actor, String summary, String details) {
        eventPublisher.publishEvent(new AuditEvent(action, actor == null ? ACTOR : actor, "PLATFORM", null,
                null, null, summary, details, false));
    }
}
