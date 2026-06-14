package com.pheme.phemenotify.persistence.converter;

import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.EventTypeRegistry;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Converter(autoApply = true)
@Component
@RequiredArgsConstructor
public class EventTypeAttributeConverter implements AttributeConverter<EventType, String> {

  private final EventTypeRegistry registry;

  @Override
  public String convertToDatabaseColumn(EventType eventType) {
    return eventType != null ? eventType.getCode() : null;
  }

  @Override
  public EventType convertToEntityAttribute(String code) {
    if (code == null) {
      return null;
    }

    if (registry == null) {
      throw new IllegalStateException(
          "EventTypeAttributeConverter is not Spring-managed - EventTypeRegistry is null. "
              + "Hibernate likely instantiated this converter directly instead of using the Spring bean.");
    }

    return registry
        .findByCode(code)
        .orElseThrow(() -> new IllegalArgumentException("Unknown event type: " + code));
  }
}
