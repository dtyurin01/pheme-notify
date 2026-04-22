package com.pheme.phemenotify.persistence.converter;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.utils.EnumUtils;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ChannelAttributeConverter implements AttributeConverter<Channel, String> {

    @Override
    public String convertToDatabaseColumn(Channel channel) {
        return channel != null ? channel.name() : null;
    }

    @Override
    public Channel convertToEntityAttribute(String code) {
        return EnumUtils.fromValue(Channel.class, code);
    }
}
