package com.smartdeploy.alert;

import com.smartdeploy.application.Application;
import com.smartdeploy.environment.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

    Optional<Alert> findByApplicationAndEnvironmentAndTypeAndStatus(
            Application application,
            Environment environment,
            AlertType type,
            AlertStatus status
    );

    List<Alert> findByApplicationAndEnvironmentAndStatusOrderByCreatedAtDesc(
            Application application,
            Environment environment,
            AlertStatus status
    );

    List<Alert> findByApplicationAndEnvironmentOrderByCreatedAtDesc(
            Application application,
            Environment environment
    );

    List<Alert> findAllByOrderByCreatedAtDesc();

    List<Alert> findByStatusOrderByCreatedAtDesc(
            AlertStatus status
    );

    long countByStatus(
            AlertStatus status
    );

}