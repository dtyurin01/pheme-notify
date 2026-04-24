package com.pheme.phemenotify.persistence.converter;

import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationStatusConverterTest {
    private final NotificationStatusConverter converter = new NotificationStatusConverter();

    @Test
    void shouldReturnName_whenStatusIsDelivered(){
        String result = converter.convertToDatabaseColumn(NotificationStatus.DELIVERED);

        assertEquals("DELIVERED", result);
    }

    @Test
    void shouldReturnNull_whenStatusIsNull() {
        String result = converter.convertToDatabaseColumn(null);

        assertNull(result);
    }


    @Test
    void shouldReturnStatus_whenCodeIsLowercase() {
        NotificationStatus result = converter.convertToEntityAttribute("delivered");

        assertEquals(NotificationStatus.DELIVERED, result);
    }

    @Test
    void shouldReturnNull_whenCodeIsNull() {
        NotificationStatus result = converter.convertToEntityAttribute(null);

        assertNull(result);
    }

    @Test
    void shouldThrowException_whenCodeIsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> {
            converter.convertToEntityAttribute("not_a_valid_status");
        });
    }
}
