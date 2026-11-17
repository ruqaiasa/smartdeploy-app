package com.smartdeploy.deployment;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stocke le statut en texte simple. Avec @Enumerated(STRING), Hibernate crée une contrainte
 * CHECK figée sur les valeurs connues à la création de la table, et ne la met jamais à jour
 * (ddl-auto: update) : les nouveaux statuts (approbation) seraient refusés par la base.
 * Même solution que pour les actions du journal (AuditActionConverter).
 */
@Converter
public class DeploymentStatusConverter implements AttributeConverter<DeploymentStatus, String> {

    @Override
    public String convertToDatabaseColumn(DeploymentStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public DeploymentStatus convertToEntityAttribute(String value) {
        return value == null ? null : DeploymentStatus.valueOf(value);
    }
}
