package com.pheme.phemenotify.messaging.event;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.EventType; // Исправлено: добавлен импорт

import java.time.Instant;
import java.util.Map;

public record NotificationEvent(
        String id,
        String userId,
        EventType eventType,
        Channel channel,
        Map<String, String> payload,
        Instant occurredAt
) {
}