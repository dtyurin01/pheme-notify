package com.pheme.phemenotify.persistence.entity;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class EventTypeRegistry {
    private final Map<String, EventType> registry;

    public EventTypeRegistry(List<EventType> eventTypes) {
        this.registry = eventTypes.stream()
            .collect(Collectors.toUnmodifiableMap(
                EventType::getCode,
                type -> type,
                (existing, replacement) -> {
                    throw new IllegalStateException("Duplicate EventType code detected: " + existing.getCode());
                }));
    }

    // string get
    public EventType getByCode(String code) {
        var eventType = registry.get(code);
        if (eventType == null) {
            throw new IllegalArgumentException("Unknown EventType code: " + code);
        }
        return eventType;
    }

    // nullable get
    public Optional<EventType> findByCode(String code) {
        return Optional.ofNullable(registry.get(code));
    }
}
