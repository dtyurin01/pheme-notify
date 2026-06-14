package com.pheme.phemenotify.messaging.event;

import com.pheme.phemenotify.persistence.entity.Channel;
import java.time.Instant;
import java.util.Map;

public record NotificationEvent(
    String id,
    String userId,
    String eventType,
    Channel channel,
    Map<String, String> payload,
    Instant occurredAt) {}
