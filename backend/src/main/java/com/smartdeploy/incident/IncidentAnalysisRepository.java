package com.smartdeploy.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IncidentAnalysisRepository extends JpaRepository<IncidentAnalysis, Long> {

    Optional<IncidentAnalysis> findFirstByDeploymentIdOrderByIdDesc(Long deploymentId);
}
