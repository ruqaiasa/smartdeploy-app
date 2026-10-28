package com.smartdeploy.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ApplicationRepository
        extends JpaRepository<Application, Long> {

    Optional<Application> findByIdAndDeletedFalse(
            Long id
    );

    Optional<Application> findByIdAndDeletedTrue(
            Long id
    );

    boolean existsByNameIgnoreCaseAndDeletedFalse(
            String name
    );

    boolean existsByNameIgnoreCaseAndDeletedTrue(
            String name
    );

    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(
            String name,
            Long id
    );

    boolean existsByNameIgnoreCaseAndDeletedTrueAndIdNot(
            String name,
            Long id
    );

    @Query("""
            SELECT application
            FROM Application application
            WHERE application.deleted = false

              AND (
                    LOWER(application.name)
                        LIKE LOWER(CONCAT('%', :search, '%'))

                    OR (
                        application.description IS NOT NULL
                        AND LOWER(application.description)
                            LIKE LOWER(CONCAT('%', :search, '%'))
                    )

                    OR LOWER(application.repositoryUrl)
                        LIKE LOWER(CONCAT('%', :search, '%'))
              )

              AND (
                    :technology IS NULL
                    OR application.technology = :technology
              )

              AND (
                    :active IS NULL
                    OR application.active = :active
              )
            """)
    Page<Application> searchApplications(
            @Param("search")
            String search,

            @Param("technology")
            Technology technology,

            @Param("active")
            Boolean active,

            Pageable pageable
    );

    long countByDeletedFalse();

    long countByActiveTrueAndDeletedFalse();

    
}