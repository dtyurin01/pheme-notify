package com.pheme.phemenotify.persistence.converter;

import com.pheme.phemenotify.persistence.entity.Channel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelAttributeConverterTest {
    private final ChannelAttributeConverter converter = new ChannelAttributeConverter();

    @Test
    void shouldReturnName_whenChannelIsEmail() {
        String result = converter.convertToDatabaseColumn(Channel.EMAIL);

        assertEquals("EMAIL", result);
    }

    @Test
    void shouldReturnNull_whenChannelIsNull() {
        String result = converter.convertToDatabaseColumn(null);

        assertNull(result);
    }


    @Test
    void shouldReturnChannel_whenCodeIsLowercase() {
        Channel result = converter.convertToEntityAttribute("email");

        assertEquals(Channel.EMAIL, result);
    }

    @Test
    void shouldReturnNull_whenCodeIsNull() {
        Channel result = converter.convertToEntityAttribute(null);

        assertNull(result);
    }

    @Test
    void shouldThrowException_whenCodeIsUnknown() {
        // Проверка, что при передаче неизвестного значения выбрасывается IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToEntityAttribute("unknown_channel_code");
        });
    }
}
