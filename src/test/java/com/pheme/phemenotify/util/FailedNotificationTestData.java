package com.pheme.phemenotify.util;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class FailedNotificationTestData {

    public static FailedNotification pending() {
        return pending(0);
    }

    public static FailedNotification pending(int retryCount) {
        return FailedNotification.builder()
                .id(UUID.randomUUID())
                .userId("user-1")
                .channel(Channel.EMAIL)
                .eventType(new OrderCompletedEventType())
                .retryCount(retryCount)
                .status(NotificationStatus.PENDING)
                .eventPayload(Map.of(
                        "id", "event-1",
                        "userId", "user-1",
                        "occurredAt", Instant.now().toString(),
                        "payload", Map.of("email", "user@example.com")
                ))
                .build();
    }
}