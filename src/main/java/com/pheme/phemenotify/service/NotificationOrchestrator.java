package com.pheme.phemenotify.service;


import com.pheme.phemenotify.infrastructure.redis.RedisDeduplicationAdapter;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.UserPreferences;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import com.pheme.phemenotify.provider.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

    private static final String IDEMPOTENCY_KEY_FORMAT = "%s:%s";

    private final RedisDeduplicationAdapter deduplicationAdapter;
    private final UserPreferenceRepository userPreferenceRepository;
    private final NotificationRepository notificationRepository;
    private final ProviderRegistry providerRegistry;

    public void process(NotificationEvent notificationEvent) {
        Optional<UserPreferences> preferences = userPreferenceRepository.findByUserId(notificationEvent.userId());

        if (preferences.isEmpty()) {
            log.warn("No user preferences found for user {}, skipping", notificationEvent.userId());
            return;
        }

        if (preferences.get().getEnabledChannels().isEmpty()) {
            log.warn("User {} has no enabled channels, skipping", notificationEvent.userId());
            return;
        }

        if (!deduplicationAdapter.isNew(notificationEvent.id())) {
            log.warn("Duplicate event {}, skipping", notificationEvent.id());
            return;
        }

        for (Channel channel : preferences.get().getEnabledChannels()) {
            processChannel(notificationEvent, channel);
        }
    }

    private void processChannel(NotificationEvent notificationEvent, Channel channel) {
        String idempotencyKey = IDEMPOTENCY_KEY_FORMAT.formatted(notificationEvent.id(), channel);
        Notification notification = Notification.pending(
                notificationEvent.userId(),
                notificationEvent.eventType(),
                channel,
                idempotencyKey
        );
        try {
            notificationRepository.save(notification);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate notification for key {}, skipping", idempotencyKey);
            return;
        }

        try {
            String renderedTemplate = "TODO: render via TemplateService";
            providerRegistry.getProvider(channel).send(notificationEvent, renderedTemplate);
            notification.setStatus(NotificationStatus.DELIVERED);
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);
            log.info("Notification delivered to user {} via {}", notificationEvent.userId(), channel);
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
            log.error("Failed to send via {}: {}", channel, e.getClass().getSimpleName(), e);
        }
    }
}
