package com.smartdeploy.deployment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeploymentKubernetesConfigRepository
        extends JpaRepository<DeploymentKubernetesConfig, Long> {

    Optional<DeploymentKubernetesConfig> findByDeploymentId(Long deploymentId);
}