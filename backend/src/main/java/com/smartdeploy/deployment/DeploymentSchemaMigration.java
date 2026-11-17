package com.smartdeploy.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Supprime, une fois, la contrainte CHECK que Hibernate avait figée sur l'ancienne liste
 * des statuts de déploiement (voir DeploymentStatusConverter). Idempotent : sans effet
 * si la contrainte n'existe plus. Une migration versionnée (Flyway) ferait mieux à terme.
 */
@Component
public class DeploymentSchemaMigration {

    private static final Logger log = LoggerFactory.getLogger(DeploymentSchemaMigration.class);
    private final JdbcTemplate jdbc;

    public DeploymentSchemaMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropFrozenStatusConstraint() {
        try {
            jdbc.execute("ALTER TABLE deployments DROP CONSTRAINT IF EXISTS deployments_status_check");
            log.info("Schéma : contrainte figée sur les statuts de déploiement retirée (si elle existait)");
        } catch (RuntimeException exception) {
            log.warn("Schéma : contrainte des statuts non retirée ({})", exception.getMessage());
        }
    }
}
