package com.pheme.phemenotify.persistence.converter;

import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import static org.junit.jupiter.api.Assertions.*;

public class NotificationStatusConverterTest {
    private final NotificationStatusConverter converter = new NotificationStatusConverter();

    @Test
    void shouldReturnName_whenStatusIsDelivered() {
        PGobject result = converter.convertToDatabaseColumn(NotificationStatus.DELIVERED);

        assertNotNull(result);
        assertEquals("notification_status", result.getType());
        assertEquals("DELIVERED", result.getValue());
    }

    @Test
    void shouldReturnNull_whenStatusIsNull() {
        PGobject result = converter.convertToDatabaseColumn(null);

        assertNull(result);
    }

    @Test
    void shouldReturnStatus_whenCodeIsLowercase() {
        NotificationStatus result = converter.convertToEntityAttribute(pgObject("notification_status", "delivered"));

        assertEquals(NotificationStatus.DELIVERED, result);
    }

    @Test
    void shouldReturnNull_whenCodeIsNull() {
        NotificationStatus result = converter.convertToEntityAttribute(null);

        assertNull(result);
    }

    @Test
    void shouldThrowException_whenCodeIsUnknown() {
        assertThrows(IllegalArgumentException.class,
            () -> converter.convertToEntityAttribute(pgObject("notification_status", "not_a_valid_status")));
    }

    private PGobject pgObject(String type, String value) {
        try {
            PGobject result = new PGobject();
            result.setType(type);
            result.setValue(value);
            return result;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
