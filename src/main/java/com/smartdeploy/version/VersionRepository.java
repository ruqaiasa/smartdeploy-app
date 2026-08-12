package com.smartdeploy.version;

import com.smartdeploy.application.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface VersionRepository extends
        JpaRepository<Version, Long>,
        JpaSpecificationExecutor<Version> {

    boolean existsByApplicationAndVersionAndDeletedFalse(
            Application application,
            String version
    );

    Optional<Version> findByIdAndDeletedFalse(Long id);

    List<Version> findAllByDeletedFalse();

    List<Version> findAllByApplicationAndDeletedFalse(
            Application application
    );
}