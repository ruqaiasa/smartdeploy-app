package com.smartdeploy.deployment;

import com.smartdeploy.environment.Environment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends
        JpaRepository<Deployment, Long>,
        JpaSpecificationExecutor<Deployment> {

    Optional<Deployment> findByIdAndDeletedFalse(Long id);

    /** Déploiements encore actifs, examinés par le garde-fou. */
    List<Deployment> findByStatusInAndDeletedFalse(Collection<DeploymentStatus> statuses);

    /** Déploiement en service : le plus récent déploiement réussi de l'environnement. */
    Optional<Deployment> findFirstByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Environment environment,
            DeploymentStatus status
    );

    /** Déploiements réussis d'un environnement, du plus récent au plus ancien. */
    List<Deployment> findByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(
            Environment environment,
            DeploymentStatus status
    );

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

    /** Score de confiance : derniers déploiements d'un environnement. */
    List<Deployment> findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(Long environmentId);

    /** Score de confiance : historique d'une version, tous environnements. */
    List<Deployment> findByVersion_IdAndDeletedFalse(Long versionId);

    /** Indicateurs DORA : déploiements d'une période. */
    List<Deployment> findByCreatedAtAfterAndDeletedFalse(java.time.LocalDateTime after);
}