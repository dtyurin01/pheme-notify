package com.pheme.phemenotify.messaging.consumer;


import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import com.pheme.phemenotify.util.NotificationTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class NotificationEventConsumerTest {

    @Mock
    NotificationOrchestrator notificationOrchestrator;

    @Mock
    FailedNotificationRepository failedNotificationRepository;

    @InjectMocks
    NotificationEventConsumer notificationEventConsumer;


    // --- handleEvent ---

    @Test
    void shouldDelegateToOrchestrator_whenEventReceived() {
        NotificationEvent notificationEvent = NotificationTestData.defaultEvent();

        notificationEventConsumer.handleEvent(notificationEvent);

        verify(notificationOrchestrator).process(notificationEvent);
    }

    @Test
    void shouldPropagateException_whenOrchestratorThrows() {
        NotificationEvent notificationEvent = NotificationTestData.defaultEvent();
        doThrow(new RuntimeException("processing failed"))
            .when(notificationOrchestrator).process(notificationEvent);

        assertThatThrownBy(() -> notificationEventConsumer.handleEvent(notificationEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("processing failed");
    }


    // --- handleDlt ---

    @Test
    void shouldSaveFailedNotification_whenDltHandlerCalled() {
        NotificationEvent notificationEvent = NotificationTestData.defaultEvent();

        notificationEventConsumer.handleDlt(
            notificationEvent,
            "notification.events.dlt",
            "timeout after 3 retries");

        ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);

        verify(failedNotificationRepository).save(captor.capture());

        FailedNotification failedNotification = captor.getValue();

        assertThat(failedNotification.getUserId()).isEqualTo(notificationEvent.userId());
        assertThat(failedNotification.getChannel()).isEqualTo(notificationEvent.channel());
        assertThat(failedNotification.getEventType()).isEqualTo(notificationEvent.eventType());

        assertThat(failedNotification.getErrorMessage()).isEqualTo("timeout after 3 retries");
    }

    @Test
    void shouldIncludeEventIdAndOccurredAt_whenBuildingDltPayload() {
        NotificationEvent notificationEvent = NotificationTestData.defaultEvent();

        notificationEventConsumer.handleDlt(
            notificationEvent,
            "notification.events.dlt",
            "SMTP Error");

        ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);

        verify(failedNotificationRepository).save(captor.capture());

        var payload = captor.getValue().getEventPayload();
        assertThat(payload).containsKey("id");
        assertThat(payload).containsKey("occurredAt");
        assertThat(payload.get("id")).isEqualTo(notificationEvent.id());
        assertThat(payload.get("occurredAt")).isEqualTo(notificationEvent.occurredAt().toString());
    }
}
