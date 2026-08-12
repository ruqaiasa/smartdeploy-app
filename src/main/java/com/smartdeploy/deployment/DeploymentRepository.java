package com.smartdeploy.deployment;

import com.smartdeploy.environment.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface DeploymentRepository extends
        JpaRepository<Deployment, Long>,
        JpaSpecificationExecutor<Deployment> {

    Optional<Deployment> findByIdAndDeletedFalse(Long id);

    boolean existsByEnvironmentAndStatusInAndDeletedFalse(
            Environment environment,
            Iterable<DeploymentStatus> statuses
    );
}