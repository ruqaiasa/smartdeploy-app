package com.smartdeploy.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface IncidentAnalysisRepository extends JpaRepository<IncidentAnalysis, Long> {

    Optional<IncidentAnalysis> findFirstByDeploymentIdOrderByIdDesc(Long deploymentId);

    /** Déploiements distincts touchés par un incident (une analyse relancée ne compte qu'une fois). */
    @Query("select count(distinct a.deploymentId) from IncidentAnalysis a where a.deploymentId in :ids and a.createdAt > :after")
    long countIncidentDeployments(@Param("ids") Collection<Long> deploymentIds, @Param("after") LocalDateTime after);
}
