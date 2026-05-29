package com.pheme.phemenotify.messaging.event;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class EventTypeDeserializer extends JsonDeserializer<EventType> {

    private final EventTypeRegistry registry;

    @Override
    public EventType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String code = p.getText();

        if (code == null || code.isBlank()) {
            return null;
        }

        return registry.findByCode(code)
            .orElseThrow(() -> new IllegalArgumentException("Unknown event type code: " + code));
    }
}
