package com.pheme.phemenotify.persistence.converter;

import static org.junit.jupiter.api.Assertions.*;

import com.pheme.phemenotify.persistence.entity.Channel;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

class ChannelAttributeConverterTest {
  private final ChannelAttributeConverter converter = new ChannelAttributeConverter();

  @Test
  void shouldReturnName_whenChannelIsEmail() {
    PGobject result = converter.convertToDatabaseColumn(Channel.EMAIL);

    assertNotNull(result);
    assertEquals("notification_channel", result.getType());
    assertEquals("EMAIL", result.getValue());
  }

  @Test
  void shouldReturnNull_whenChannelIsNull() {
    PGobject result = converter.convertToDatabaseColumn(null);

    assertNull(result);
  }

  @Test
  void shouldReturnChannel_whenCodeIsLowercase() {
    Channel result = converter.convertToEntityAttribute(pgObject("notification_channel", "email"));

    assertEquals(Channel.EMAIL, result);
  }

  @Test
  void shouldReturnNull_whenCodeIsNull() {
    Channel result = converter.convertToEntityAttribute(null);

    assertNull(result);
  }

  @Test
  void shouldThrowException_whenCodeIsUnknown() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            converter.convertToEntityAttribute(
                pgObject("notification_channel", "unknown_channel_code")));
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
