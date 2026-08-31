package com.pheme.phemenotify.messaging.event;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.EventTypeRegistry;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventTypeDeserializerTest {

  @Mock private EventType eventType;

  @Mock private EventTypeRegistry registry;

  private EventTypeDeserializer deserializer;

  private final ObjectMapper mapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    deserializer = new EventTypeDeserializer(registry);
  }

  @Test
  void shouldReturnEventType_whenCodeIsValid() throws IOException {
    JsonParser parser =
        mapper.createParser(
            """
            "ORDER_CREATED"
            """);
    parser.nextToken();

    when(registry.findByCode("ORDER_CREATED")).thenReturn(java.util.Optional.of(eventType));

    EventType result = deserializer.deserialize(parser, null);

    assertEquals(eventType, result);
  }

  @Test
  void shouldReturnNull_whenCodeIsBlank() throws IOException {
    JsonParser parser = mapper.createParser("\"\"");
    parser.nextToken();

    EventType result = deserializer.deserialize(parser, null);

    assertNull(result);
    verifyNoInteractions(registry);
  }

  @Test
  void shouldThrowUnknown_whenCodeIsUnknown() throws IOException {
    String unknownCode = "UNKNOWN_CODE";
    JsonParser parser =
        mapper.createParser(
            """
            "UNKNOWN_CODE"
            """);
    parser.nextToken();

    when(registry.findByCode(unknownCode)).thenReturn(java.util.Optional.empty());

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> deserializer.deserialize(parser, null));

    assertTrue(exception.getMessage().contains(unknownCode));
  }
}
