package com.smartdeploy.deployment;

import com.smartdeploy.environment.Environment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends
        JpaRepository<Deployment, Long>,
        JpaSpecificationExecutor<Deployment> {

    Optional<Deployment> findByIdAndDeletedFalse(Long id);

    /**
     * Lecture avec verrou d'écriture : sérialise les callbacks Jenkins
     * concurrents et le marquage FAILED du lanceur de pipeline.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Deployment d where d.id = :id and d.deleted = false")
    Optional<Deployment> findActiveByIdForUpdate(@Param("id") Long id);

    boolean existsByEnvironmentAndStatusInAndDeletedFalse(
            Environment environment,
            Iterable<DeploymentStatus> statuses
    );

    @EntityGraph(
            attributePaths = {
                    "environment",
                    "environment.application"
            }
    )
    List<Deployment>
    findByStatusAndDeletedFalseAndKubernetesDeploymentNameIsNotNullOrderByFinishedAtDesc(
            DeploymentStatus status
    );
    long countByDeletedFalse();

    long countByStatusAndDeletedFalse(
            DeploymentStatus status
    );

    @EntityGraph(
            attributePaths = {
                    "environment",
                    "environment.application",
                    "version"
            }
    )
    List<Deployment> findTop5ByDeletedFalseOrderByCreatedAtDesc();
}