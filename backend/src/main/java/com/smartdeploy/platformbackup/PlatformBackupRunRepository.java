package com.smartdeploy.platformbackup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformBackupRunRepository extends JpaRepository<PlatformBackupRun, Long> {

    Optional<PlatformBackupRun> findFirstByOrderByStartedAtDesc();

    Optional<PlatformBackupRun> findFirstBySuccessTrueOrderByStartedAtDesc();
}
