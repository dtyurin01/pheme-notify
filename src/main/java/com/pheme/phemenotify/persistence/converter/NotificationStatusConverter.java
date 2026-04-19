package com.pheme.phemenotify.persistence.converter;


import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.utils.EnumUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class NotificationStatusConverter implements AttributeConverter<NotificationStatus, String> {

    @Override
    public String convertToDatabaseColumn(NotificationStatus status) {
        return status != null ? status.name() : null;
    }

    @Override
    public NotificationStatus convertToEntityAttribute(String code) {
        return  EnumUtils.fromValue(NotificationStatus.class, code);
    }
}
