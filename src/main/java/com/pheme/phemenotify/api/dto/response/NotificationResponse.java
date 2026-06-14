package com.pheme.phemenotify.api.dto.response;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String userId,
    Channel channel,
    String eventType,
    NotificationStatus status,
    String errorMessage,
    Instant createdAt,
    Instant sentAt) {}
