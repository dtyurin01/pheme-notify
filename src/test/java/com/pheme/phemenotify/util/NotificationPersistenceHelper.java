package com.pheme.phemenotify.util;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import java.time.Instant;

public class NotificationPersistenceHelper {

  public static void saveWithDate(
      NotificationRepository repo,
      EventType eventType,
      Instant createdAt,
      Channel channel,
      NotificationStatus status,
      String key) {
    Notification saved = repo.save(buildNotification(key, channel, status, eventType));
    repo.updateCreatedAt(saved.getId(), createdAt);
  }

  public static void saveWithDateAndType(
      NotificationRepository repo,
      Instant createdAt,
      Channel channel,
      NotificationStatus status,
      String key,
      EventType eventType) {
    Notification saved = repo.save(buildNotification(key, channel, status, eventType));
    repo.updateCreatedAt(saved.getId(), createdAt);
  }

  public static Notification buildNotification(
      String key, Channel channel, NotificationStatus status, EventType eventType) {
    return Notification.builder()
        .userId("user-123")
        .channel(channel)
        .eventType(eventType)
        .idempotencyKey(key)
        .status(status)
        .build();
  }
}
