package com.pheme.phemenotify.persistence.converter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.EventTypeRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventTypeAttributeConverterTest {

  @Mock private EventTypeRegistry registry;

  @Mock private EventType eventType;

  private EventTypeAttributeConverter converter;

  @BeforeEach
  void setUp() {
    converter = new EventTypeAttributeConverter(registry);
  }

  @Test
  void shouldReturnCode_whenEventTypeIsValid() {
    when(eventType.getCode()).thenReturn("ORDER_CREATED");

    String result = converter.convertToDatabaseColumn(eventType);

    assertEquals("ORDER_CREATED", result);
  }

  @Test
  void shouldReturnNull_whenEventTypeIsNull() {
    String result = converter.convertToDatabaseColumn(null);

    assertNull(result);
  }

  @Test
  void shouldReturnEventType_whenCodeIsValid() {
    String code = "ORDER_COMPLETED";
    when(registry.findByCode(code)).thenReturn(Optional.of(eventType));

    EventType result = converter.convertToEntityAttribute(code);

    assertEquals(eventType, result);
    verify(registry).findByCode(code);
  }

  @Test
  void shouldReturnNull_whenCodeIsNull() {
    assertNull(converter.convertToEntityAttribute(null));
    verifyNoInteractions(registry);
  }

  @Test
  void shouldThrowException_whenCodeIsUnknown() {
    String unknownCode = "UNKNOWN";
    when(registry.findByCode(anyString())).thenReturn(Optional.empty());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> converter.convertToEntityAttribute(unknownCode));

    assertEquals("Unknown event type: UNKNOWN", exception.getMessage());
  }
}
