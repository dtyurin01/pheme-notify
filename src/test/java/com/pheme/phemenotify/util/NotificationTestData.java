package com.pheme.phemenotify.util;

import com.pheme.phemenotify.api.dto.response.NotificationResponse;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class NotificationTestData {

    public static Notification defaultEntity(UUID id) {
        return Notification.builder()
            .id(id)
            .userId("user-1")
            .channel(Channel.EMAIL)
            .eventType(new OrderCompletedEventType())
            .status(NotificationStatus.DELIVERED)
            .errorMessage(null)
            .createdAt(Instant.now())
            .sentAt(Instant.now())
            .build();
    }

    public static Notification entityWith(UUID id, NotificationStatus status, String errorMessage) {
        return Notification.builder()
            .id(id)
            .userId("user-1")
            .channel(Channel.EMAIL)
            .eventType(new OrderCompletedEventType())
            .status(status)
            .errorMessage(errorMessage)
            .createdAt(Instant.now())
            .sentAt(Instant.now())
            .build();
    }

    public static NotificationResponse defaultResponse(UUID id) {
        return new NotificationResponse(
            id,
            "user-1",
            Channel.EMAIL,
            OrderCompletedEventType.CODE,
            NotificationStatus.DELIVERED,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    public static NotificationResponse responseWith(UUID id, NotificationStatus status, String errorMessage) {
        return new NotificationResponse(
            id,
            "user-1",
            Channel.EMAIL,
            OrderCompletedEventType.CODE,
            status,
            errorMessage,
            Instant.now(),
            Instant.now()
        );
    }


    /// NOTIFICATION EVENT TEST DATA

    public static NotificationEvent defaultEvent() {
        return new NotificationEvent(
            "event-1",
            "user-1",
            OrderCompletedEventType.CODE,
            Channel.EMAIL,
            Map.of("email", "user@example.com"),
            Instant.now()
        );
    }

    public static NotificationEvent eventWithPayload(Map<String, String> payload) {
        return new NotificationEvent(
            "event-1",
            "user-1",
            OrderCompletedEventType.CODE,
            Channel.EMAIL,
            payload,
            Instant.now()
        );
    }


}
