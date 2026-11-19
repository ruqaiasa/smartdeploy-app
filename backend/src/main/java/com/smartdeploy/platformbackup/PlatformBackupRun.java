package com.smartdeploy.platformbackup;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Une exécution de la sauvegarde de la base de SmartDeploy. */
@Entity
@Table(name = "platform_backup_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBackupRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "local_file", length = 500)
    private String localFile;

    /** Copie envoyée hors du poste (Azure) ? */
    @Column(nullable = false)
    private boolean uploaded;

    @Column(name = "triggered_by", length = 150)
    private String triggeredBy;

    @Column(length = 1000)
    private String message;
}
