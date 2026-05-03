package com.pheme.phemenotify.persistence.converter;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.utils.EnumUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

@Converter(autoApply = true)
public class ChannelAttributeConverter implements AttributeConverter<Channel, PGobject> {

    @Override
    public PGobject convertToDatabaseColumn(Channel channel) {
        if (channel == null) {
            return null;
        }

        try {
            PGobject value = new PGobject();
            value.setType("notification_channel");
            value.setValue(channel.name());
            return value;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to convert Channel to PostgreSQL enum", e);
        }
    }

    @Override
    public Channel convertToEntityAttribute(PGobject dbData) {
        return dbData != null ? EnumUtils.fromValue(Channel.class, dbData.getValue()) : null;
    }
}
