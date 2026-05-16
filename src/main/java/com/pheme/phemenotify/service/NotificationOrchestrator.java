package com.pheme.phemenotify.service;


import com.pheme.phemenotify.api.exception.RateLimitExceededException;
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
    private final RateLimitService rateLimitService;
    private final TemplateService templateService;

    public boolean processRetry(NotificationEvent notificationEvent) {
        Optional<UserPreferences> preferences = userPreferenceRepository.findByUserId(notificationEvent.userId());

        if (preferences.isEmpty()) {
            log.warn("No user preferences found for user {}, skipping retry", notificationEvent.userId());
            return false;
        }

        if (preferences.get().getEnabledChannels().isEmpty()) {
            log.warn("User {} has no enabled channels, skipping retry", notificationEvent.userId());
            return false;
        }

        return processChannel(notificationEvent, notificationEvent.channel());
    }

    public void process(NotificationEvent notificationEvent) {
        if (!deduplicationAdapter.isNew(notificationEvent.id())) {
            log.warn("Duplicate event {}, skipping", notificationEvent.id());
            return;
        }

        Optional<UserPreferences> preferences = userPreferenceRepository.findByUserId(notificationEvent.userId());

        if (preferences.isEmpty()) {
            log.warn("No user preferences found for user {}, skipping", notificationEvent.userId());
            return;
        }

        if (preferences.get().getEnabledChannels().isEmpty()) {
            log.warn("User {} has no enabled channels, skipping", notificationEvent.userId());
            return;
        }

        for (Channel channel : preferences.get().getEnabledChannels()) {
            processChannel(notificationEvent, channel);
        }
    }

    private boolean processChannel(NotificationEvent notificationEvent, Channel channel) {
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
            return false;
        }

        try {
            rateLimitService.checkLimit(notificationEvent.userId(), channel);
        } catch (RateLimitExceededException e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
            log.warn("Rate limit exceeded for event {} on channel {}, skipping", notificationEvent.id(), channel);
            return false;
        }

        try {
            String renderedTemplate = templateService.render(
                    notificationEvent.eventType(),
                    channel,
                    notificationEvent.payload()
            );
            providerRegistry.getProvider(channel).send(notificationEvent, renderedTemplate);
            notification.setStatus(NotificationStatus.DELIVERED);
            notification.setSentAt(Instant.now());
            notificationRepository.save(notification);
            log.info("Notification delivered to user {} via {}", notificationEvent.userId(), channel);
            return true;
        } catch (Exception e) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
            notificationRepository.save(notification);
            log.error("Failed to send via {}: {}", channel, e.getClass().getSimpleName(), e);
            return false;
        }
    }
}
