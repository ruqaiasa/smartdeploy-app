package com.smartdeploy.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeploymentCommentReadRepository extends JpaRepository<DeploymentCommentRead, Long> {

    Optional<DeploymentCommentRead> findByUserIdAndDeploymentId(Long userId, Long deploymentId);

    List<DeploymentCommentRead> findByUserId(Long userId);
}
