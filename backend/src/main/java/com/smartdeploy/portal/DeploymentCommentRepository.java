package com.smartdeploy.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeploymentCommentRepository extends JpaRepository<DeploymentComment, Long> {

    List<DeploymentComment> findByDeploymentIdOrderByCreatedAtAsc(Long deploymentId);
}
