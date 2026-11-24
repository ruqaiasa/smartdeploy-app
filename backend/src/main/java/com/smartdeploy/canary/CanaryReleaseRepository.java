package com.smartdeploy.canary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CanaryReleaseRepository extends JpaRepository<CanaryRelease, Long> {

    Optional<CanaryRelease> findByDeploymentId(Long deploymentId);

    boolean existsByDeploymentId(Long deploymentId);

    List<CanaryRelease> findByEnvironmentIdAndState(Long environmentId, CanaryRelease.State state);

    List<CanaryRelease> findByState(CanaryRelease.State state);
}
