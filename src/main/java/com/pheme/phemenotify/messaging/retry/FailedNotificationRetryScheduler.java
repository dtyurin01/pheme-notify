package com.pheme.phemenotify.messaging.retry;

import com.pheme.phemenotify.config.RetrySchedulerProperties;
import com.pheme.phemenotify.infrastructure.metrics.NotificationMetrics;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedNotificationRetryScheduler {

  private final FailedNotificationRepository failedNotificationRepository;
  private final NotificationOrchestrator notificationOrchestrator;
  private final RetrySchedulerProperties properties;
  private final NotificationMetrics notificationMetrics;

  @Scheduled(fixedDelayString = "${notification.retry-scheduler.fixed-delay}")
  public void retryFailedNotifications() {
    List<FailedNotification> pending =
        failedNotificationRepository.findByStatusAndNextRetryAtBefore(
            NotificationStatus.PENDING, Instant.now());

    log.info("Retry scheduler: found {} pending failed notifications", pending.size());

    for (FailedNotification failed : pending) {
      retry(failed);
    }
  }

  private void retry(FailedNotification failed) {
    NotificationEvent event = toEvent(failed);
    boolean success = notificationOrchestrator.processRetry(event);

    if (success) {
      failed.setStatus(NotificationStatus.DELIVERED);
      failed.setLastRetryAt(Instant.now());
      failedNotificationRepository.save(failed);
      notificationMetrics.incrementRetrySuccess();
      log.info("Retry succeeded: id={}", failed.getId());
    } else {
      int newCount = failed.getRetryCount() + 1;
      failed.setRetryCount(newCount);
      failed.setLastRetryAt(Instant.now());

      if (newCount >= properties.getMaxAttempts()) {
        failed.setStatus(NotificationStatus.FAILED);
        notificationMetrics.incrementRetryExhausted();
        log.error("Retry exhausted: id={}, marking FAILED", failed.getId());
      } else {
        long backoffSeconds = properties.getBackoffBase().toSeconds() * (1L << (newCount - 1));
        failed.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
        notificationMetrics.incrementRetryFailed();
        log.warn("Retry #{} failed: id={}, next in {}s", newCount, failed.getId(), backoffSeconds);
      }

      failedNotificationRepository.save(failed);
    }
  }

  @SuppressWarnings("unchecked")
  private NotificationEvent toEvent(FailedNotification failed) {
    Map<String, Object> raw = failed.getEventPayload();

    return new NotificationEvent(
        (String) raw.get("id"),
        (String) raw.get("userId"),
        failed.getEventType().getCode(),
        failed.getChannel(),
        (Map<String, String>) raw.get("payload"),
        Instant.parse((String) raw.get("occurredAt")));
  }
}
