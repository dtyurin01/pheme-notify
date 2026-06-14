package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.dto.response.NotificationResponse;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {
  private final NotificationRepository notificationRepository;

  @Transactional(readOnly = true)
  public NotificationResponse getById(UUID id) {
    return notificationRepository
        .findById(id)
        .map(this::toResponse)
        .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
  }

  @Transactional(readOnly = true)
  public NotificationResponse getByEventIdAndChannel(Channel channel, String eventId) {
    String idempotencyKey = String.format("%s:%s", eventId, channel.name());
    return notificationRepository
        .findByIdempotencyKey(idempotencyKey)
        .map(this::toResponse)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Notification not found for eventId: " + eventId + " and channel: " + channel));
  }

  private NotificationResponse toResponse(Notification entity) {
    return new NotificationResponse(
        entity.getId(),
        entity.getUserId(),
        entity.getChannel(),
        entity.getEventType().getCode(),
        entity.getStatus(),
        entity.getErrorMessage(),
        entity.getCreatedAt(),
        entity.getSentAt());
  }
}
