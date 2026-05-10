package com.pheme.phemenotify.messaging.retry;

import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class FailedNotificationRetryScheduler {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BACKOFF_BASE_SECONDS = 300;
    private static final long FIXED_DELAY = 60_000;

    private final FailedNotificationRepository failedNotificationRepository;
    private final NotificationOrchestrator notificationOrchestrator;

    @Scheduled(fixedDelay = FIXED_DELAY)
    public void retryFailedNotifications() {
        List<FailedNotification> pending = failedNotificationRepository
                .findByStatusAndNextRetryAtBefore(NotificationStatus.PENDING, Instant.now());

        log.info("Retry scheduler: found {} pending failed notifications", pending.size());

        for (FailedNotification failed : pending) {
            retry(failed);
        }
    }

    private void retry(FailedNotification failed) {
        try {
            NotificationEvent event = toEvent(failed);
            notificationOrchestrator.processRetry(event);

            failed.setStatus(NotificationStatus.DELIVERED);
            failed.setLastRetryAt(Instant.now());
            failedNotificationRepository.save(failed);

            log.info("Retry succeeded: id={}", failed.getId());
        } catch (Exception e) {
            int newCount = failed.getRetryCount() + 1;
            failed.setRetryCount(newCount);
            failed.setLastRetryAt(Instant.now());

            if (newCount >= MAX_RETRY_ATTEMPTS) {
                failed.setStatus(NotificationStatus.FAILED);
                log.error("Retry exhausted: id={}, marking FAILED", failed.getId(), e);
            } else {
                long backoffSeconds = BACKOFF_BASE_SECONDS * (1L << (newCount - 1));
                failed.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
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
                failed.getEventType(),
                failed.getChannel(),
                (Map<String, String>) raw.get("payload"),
                Instant.parse((String) raw.get("occurredAt"))
        );
    }
}