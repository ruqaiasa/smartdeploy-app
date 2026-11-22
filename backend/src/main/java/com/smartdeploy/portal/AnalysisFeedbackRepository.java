package com.smartdeploy.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisFeedbackRepository extends JpaRepository<AnalysisFeedback, Long> {

    Optional<AnalysisFeedback> findFirstByDeploymentIdOrderByIdDesc(Long deploymentId);
}
