package com.pheme.phemenotify.messaging.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pheme.phemenotify.config.RetrySchedulerProperties;
import com.pheme.phemenotify.infrastructure.metrics.NotificationMetrics;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import com.pheme.phemenotify.util.FailedNotificationTestData;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FailedNotificationRetrySchedulerTest {

  @Mock private FailedNotificationRepository failedNotificationRepository;

  @Mock private NotificationOrchestrator notificationOrchestrator;

  @InjectMocks private FailedNotificationRetryScheduler scheduler;

  @Mock private RetrySchedulerProperties properties;

  @Mock private NotificationMetrics notificationMetrics;

  @Test
  void shouldDoNothing_whenNoPendingNotifications() {
    when(failedNotificationRepository.findByStatusAndNextRetryAtBefore(any(), any()))
        .thenReturn(List.of());

    scheduler.retryFailedNotifications();

    verifyNoInteractions(notificationOrchestrator);
    verify(failedNotificationRepository, never()).save(any());
  }

  @Test
  void shouldMarkAsDelivered_whenRetrySucceeds() {
    FailedNotification failed = FailedNotificationTestData.pending();
    when(failedNotificationRepository.findByStatusAndNextRetryAtBefore(any(), any()))
        .thenReturn(List.of(failed));
    when(notificationOrchestrator.processRetry(any())).thenReturn(true);

    scheduler.retryFailedNotifications();

    ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);
    verify(failedNotificationRepository).save(captor.capture());

    FailedNotification saved = captor.getValue();
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    assertThat(saved.getLastRetryAt()).isNotNull();
    verify(notificationMetrics).incrementRetrySuccess();
  }

  @Test
  void shouldIncrementRetryCountAndScheduleNextRetry_whenRetryFails() {
    FailedNotification failed = FailedNotificationTestData.pending(0);
    when(failedNotificationRepository.findByStatusAndNextRetryAtBefore(any(), any()))
        .thenReturn(List.of(failed));
    when(properties.getMaxAttempts()).thenReturn(3);
    when(properties.getBackoffBase()).thenReturn(Duration.ofSeconds(300));
    when(notificationOrchestrator.processRetry(any())).thenReturn(false);

    scheduler.retryFailedNotifications();

    ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);
    verify(failedNotificationRepository).save(captor.capture());

    FailedNotification saved = captor.getValue();
    assertThat(saved.getRetryCount()).isEqualTo(1);
    assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
    assertThat(saved.getNextRetryAt()).isAfter(Instant.now());
    verify(notificationMetrics).incrementRetryFailed();
  }

  @Test
  void shouldMarkAsFailed_whenMaxRetriesExhausted() {
    FailedNotification failed = FailedNotificationTestData.pending(2); // MAX_RETRY_ATTEMPTS - 1
    when(failedNotificationRepository.findByStatusAndNextRetryAtBefore(any(), any()))
        .thenReturn(List.of(failed));
    when(properties.getMaxAttempts()).thenReturn(3);
    when(notificationOrchestrator.processRetry(any())).thenReturn(false);

    scheduler.retryFailedNotifications();

    ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);
    verify(failedNotificationRepository).save(captor.capture());

    assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
    verify(notificationMetrics).incrementRetryExhausted();
  }
}
