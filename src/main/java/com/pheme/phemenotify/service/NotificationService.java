package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.dto.response.NotificationResponse;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID id) {
        return notificationRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + id));
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
                entity.getSentAt()
        );
    }
}
