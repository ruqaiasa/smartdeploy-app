package com.smartdeploy.audit;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Stores the action as plain text: with @Enumerated(STRING), Hibernate creates
 * a CHECK constraint frozen on the values known when the table was created,
 * and never updates it (ddl-auto: update). New actions were then rejected.
 */
@Converter
public class AuditActionConverter implements AttributeConverter<AuditAction, String> {

    @Override
    public String convertToDatabaseColumn(AuditAction action) {
        return action == null ? null : action.name();
    }

    @Override
    public AuditAction convertToEntityAttribute(String value) {
        return value == null ? null : AuditAction.valueOf(value);
    }
}