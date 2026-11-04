package com.smartdeploy.guard;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Texte simple, sans contrainte CHECK figée (voir AuditActionConverter). */
@Converter
public class GuardStatusConverter implements AttributeConverter<GuardStatus, String> {

    @Override
    public String convertToDatabaseColumn(GuardStatus status) {
        return status == null ? null : status.name();
    }

    @Override
    public GuardStatus convertToEntityAttribute(String value) {
        return value == null ? null : GuardStatus.valueOf(value);
    }
}
