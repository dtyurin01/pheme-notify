package com.pheme.phemenotify.persistence.converter;


import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.utils.EnumUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

@Converter(autoApply = true)
public class NotificationStatusConverter implements AttributeConverter<NotificationStatus, PGobject> {

    @Override
    public PGobject convertToDatabaseColumn(NotificationStatus status) {
        if (status == null) {
            return null;
        }

        try {
            PGobject value = new PGobject();
            value.setType("notification_status");
            value.setValue(status.name());
            return value;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to convert NotificationStatus to PostgreSQL enum", e);
        }
    }

    @Override
    public NotificationStatus convertToEntityAttribute(PGobject dbData) {
        return dbData != null ? EnumUtils.fromValue(NotificationStatus.class, dbData.getValue()) : null;
    }
}
