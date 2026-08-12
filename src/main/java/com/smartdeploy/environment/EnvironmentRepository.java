package com.smartdeploy.environment;

import com.smartdeploy.application.Application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EnvironmentRepository
        extends JpaRepository<Environment, Long> {

    Optional<Environment> findByIdAndDeletedFalse(Long id);

    Optional<Environment> findByIdAndDeletedTrue(Long id);

    boolean existsByApplicationAndTypeAndDeletedFalse(
            Application application,
            EnvironmentType type
    );

    boolean existsByApplicationAndTypeAndDeletedFalseAndIdNot(
            Application application,
            EnvironmentType type,
            Long id
    );

    @Query("""
            SELECT environment
            FROM Environment environment
            JOIN FETCH environment.application application
            WHERE environment.deleted = false

              AND (
                    LOWER(environment.name)
                        LIKE LOWER(CONCAT('%', :search, '%'))

                    OR LOWER(environment.namespace)
                        LIKE LOWER(CONCAT('%', :search, '%'))

                    OR (
                        environment.description IS NOT NULL
                        AND LOWER(environment.description)
                            LIKE LOWER(CONCAT('%', :search, '%'))
                    )

                    OR LOWER(application.name)
                        LIKE LOWER(CONCAT('%', :search, '%'))
              )

              AND (
                    :type IS NULL
                    OR environment.type = :type
              )

              AND (
                    :active IS NULL
                    OR environment.active = :active
              )

              AND (
                    :applicationId IS NULL
                    OR application.id = :applicationId
              )
            """)
    Page<Environment> searchEnvironments(
            @Param("search")
            String search,

            @Param("type")
            EnvironmentType type,

            @Param("active")
            Boolean active,

            @Param("applicationId")
            Long applicationId,

            Pageable pageable
    );


    List<Environment>
    findAllByApplicationIdAndDeletedFalseOrderByCreatedAtDesc(
            Long applicationId
    );


}