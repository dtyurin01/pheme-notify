package com.pheme.phemenotify.messaging.retry;

import com.pheme.phemenotify.config.RetrySchedulerProperties;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import com.pheme.phemenotify.util.FailedNotificationTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FailedNotificationRetrySchedulerTest {

    @Mock
    private FailedNotificationRepository failedNotificationRepository;

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    @InjectMocks
    private FailedNotificationRetryScheduler scheduler;

    @Mock
    private RetrySchedulerProperties properties;


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
    }
}
