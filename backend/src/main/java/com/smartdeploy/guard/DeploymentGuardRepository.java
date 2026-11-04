package com.smartdeploy.guard;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeploymentGuardRepository extends JpaRepository<DeploymentGuard, Long> {

    Optional<DeploymentGuard> findFirstByDeploymentIdOrderByIdDesc(Long deploymentId);

    List<DeploymentGuard> findByStatus(GuardStatus status);

    List<DeploymentGuard> findByEnvironmentIdAndStatus(Long environmentId, GuardStatus status);

    List<DeploymentGuard> findByDeploymentIdIn(Collection<Long> deploymentIds);

    List<DeploymentGuard> findAllByOrderByIdDesc(Pageable pageable);
}
