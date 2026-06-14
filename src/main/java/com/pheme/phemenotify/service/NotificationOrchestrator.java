package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.exception.RateLimitExceededException;
import com.pheme.phemenotify.infrastructure.metrics.NotificationMetrics;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.EventTypeRegistry;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.UserPreferences;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import com.pheme.phemenotify.provider.ProviderRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOrchestrator {

  private static final String IDEMPOTENCY_KEY_FORMAT = "%s:%s";

  private final DeduplicationService deduplicationService;
  private final UserPreferenceRepository userPreferenceRepository;
  private final NotificationRepository notificationRepository;
  private final ProviderRegistry providerRegistry;
  private final RateLimitService rateLimitService;
  private final TemplateService templateService;
  private final EventTypeRegistry eventTypeRegistry;
  private final NotificationMetrics notificationMetrics;

  /**
   * Retries delivery for a single channel from {@link
   * com.pheme.phemenotify.persistence.entity.FailedNotification}. Skips if user preferences are
   * missing or the channel was disabled since the original attempt.
   *
   * @return true if delivered successfully
   */
  public boolean processRetry(NotificationEvent notificationEvent) {
    Optional<UserPreferences> preferences = resolvePreferences(notificationEvent.userId());
    if (preferences.isEmpty()) return false;

    if (!preferences.get().getEnabledChannels().contains(notificationEvent.channel())) {
      log.warn(
          "Channel {} is not enabled for user {}, skipping retry",
          notificationEvent.channel(),
          notificationEvent.userId());
      return false;
    }

    return processChannel(notificationEvent, notificationEvent.channel());
  }

  /**
   * Entry point for a Kafka {@link NotificationEvent}. Deduplicates via Redis, then delivers to
   * every channel enabled in user preferences. Failure on one channel does not stop delivery to the
   * others (partial failure).
   */
  public void process(NotificationEvent notificationEvent) {
    if (!deduplicationService.isNew(notificationEvent.id())) {
      log.warn("Duplicate event {}, skipping", notificationEvent.id());
      return;
    }

    Optional<UserPreferences> preferences = resolvePreferences(notificationEvent.userId());
    if (preferences.isEmpty()) return;

    for (Channel channel : preferences.get().getEnabledChannels()) {
      processChannel(notificationEvent, channel);
    }
  }

  /**
   * Loads user preferences and validates they have at least one enabled channel. Returns empty
   * (with a warning log) if preferences are missing or no channel is enabled.
   */
  private Optional<UserPreferences> resolvePreferences(String userId) {
    Optional<UserPreferences> preferences = userPreferenceRepository.findByUserId(userId);

    if (preferences.isEmpty()) {
      log.warn("No user preferences found for user {}, skipping", userId);
      return Optional.empty();
    }

    if (preferences.get().getEnabledChannels().isEmpty()) {
      log.warn("User {} has no enabled channels, skipping", userId);
      return Optional.empty();
    }

    return preferences;
  }

  /**
   * Delivers one event to one channel: 1. Persists a PENDING {@link Notification} (idempotency key
   * guards against duplicate inserts). 2. Checks the rate limit — on exceed, marks FAILED with
   * "RATE_LIMIT_EXCEEDED". 3. Renders the template and sends via the channel's provider. 4. Marks
   * DELIVERED on success or FAILED with "SEND_FAILED:&lt;ExceptionClass&gt;" on error. Records
   * {@link NotificationMetrics} (sent/failed counters and send duration) for the send step.
   *
   * @return true if delivered successfully
   */
  private boolean processChannel(NotificationEvent notificationEvent, Channel channel) {
    Optional<EventType> eventTypeOpt = eventTypeRegistry.findByCode(notificationEvent.eventType());
    if (eventTypeOpt.isEmpty()) {
      log.warn(
          "Unknown event type {} for event {}, skipping channel {}",
          notificationEvent.eventType(),
          notificationEvent.id(),
          channel);
      return false;
    }
    EventType eventType = eventTypeOpt.get();
    String idempotencyKey = IDEMPOTENCY_KEY_FORMAT.formatted(notificationEvent.id(), channel);
    Notification notification =
        Notification.pending(notificationEvent.userId(), eventType, channel, idempotencyKey);
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
      notification.setErrorMessage("RATE_LIMIT_EXCEEDED");
      notificationRepository.save(notification);
      log.warn(
          "Rate limit exceeded for event {} on channel {}: {}",
          notificationEvent.id(),
          channel,
          e.getMessage());
      return false;
    }

    Timer.Sample sample = notificationMetrics.sendTimer();
    try {
      String renderedTemplate =
          templateService.render(eventType, channel, new HashMap<>(notificationEvent.payload()));
      providerRegistry.getProvider(channel).send(notificationEvent, renderedTemplate);
      notification.setStatus(NotificationStatus.DELIVERED);
      notification.setSentAt(Instant.now());
      notificationRepository.save(notification);
      notificationMetrics.incrementSent(channel);
      log.info("Notification delivered to user {} via {}", notificationEvent.userId(), channel);
      return true;
    } catch (Exception e) {
      notification.setStatus(NotificationStatus.FAILED);
      notification.setErrorMessage("SEND_FAILED:" + e.getClass().getSimpleName());
      notificationRepository.save(notification);
      notificationMetrics.incrementFailed(channel);
      log.error("Failed to send via {}: {}", channel, e.getMessage(), e);
      return false;
    } finally {
      notificationMetrics.recordSendDuration(sample, channel);
    }
  }
}
