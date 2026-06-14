package com.pheme.phemenotify.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheme.phemenotify.infrastructure.metrics.NotificationMetrics;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.EventTypeRegistry;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import com.pheme.phemenotify.util.NotificationTestData;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
public class NotificationEventConsumerTest {

  @Mock NotificationOrchestrator notificationOrchestrator;

  @Mock FailedNotificationRepository failedNotificationRepository;

  @Mock EventTypeRegistry eventTypeRegistry;

  @Mock NotificationMetrics notificationMetrics;

  @InjectMocks NotificationEventConsumer notificationEventConsumer;

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
        .when(notificationOrchestrator)
        .process(notificationEvent);

    assertThatThrownBy(() -> notificationEventConsumer.handleEvent(notificationEvent))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("processing failed");
  }

  @Test
  void shouldPutEventIdInMdc_whenProcessingEvent() {
    NotificationEvent notificationEvent = NotificationTestData.defaultEvent();
    AtomicReference<String> mdcDuringProcessing = new AtomicReference<>();
    doAnswer(
            invocation -> {
              mdcDuringProcessing.set(MDC.get("eventId"));
              return null;
            })
        .when(notificationOrchestrator)
        .process(notificationEvent);

    notificationEventConsumer.handleEvent(notificationEvent);

    assertThat(mdcDuringProcessing.get()).isEqualTo(notificationEvent.id());
  }

  @Test
  void shouldClearMdc_whenEventProcessedSuccessfully() {
    NotificationEvent notificationEvent = NotificationTestData.defaultEvent();

    notificationEventConsumer.handleEvent(notificationEvent);

    assertThat(MDC.get("eventId")).isNull();
  }

  @Test
  void shouldClearMdc_whenOrchestratorThrows() {
    NotificationEvent notificationEvent = NotificationTestData.defaultEvent();
    doThrow(new RuntimeException("processing failed"))
        .when(notificationOrchestrator)
        .process(notificationEvent);

    assertThatThrownBy(() -> notificationEventConsumer.handleEvent(notificationEvent));

    assertThat(MDC.get("eventId")).isNull();
  }

  // --- handleDlt ---

  @Test
  void shouldSaveFailedNotification_whenDltHandlerCalled() {
    NotificationEvent notificationEvent = NotificationTestData.defaultEvent();
    when(eventTypeRegistry.findByCode(OrderCompletedEventType.CODE))
        .thenReturn(Optional.of(new OrderCompletedEventType()));

    notificationEventConsumer.handleDlt(
        notificationEvent, "notification.events.dlt", "timeout after 3 retries");

    ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);

    verify(failedNotificationRepository).save(captor.capture());

    FailedNotification failedNotification = captor.getValue();

    assertThat(failedNotification.getUserId()).isEqualTo(notificationEvent.userId());
    assertThat(failedNotification.getChannel()).isEqualTo(notificationEvent.channel());
    assertThat(failedNotification.getEventType().getCode())
        .isEqualTo(notificationEvent.eventType());

    assertThat(failedNotification.getErrorMessage()).isEqualTo("timeout after 3 retries");
  }

  @Test
  void shouldIncrementDltMetric_whenDltHandlerCalled() {
    NotificationEvent notificationEvent = NotificationTestData.defaultEvent();
    when(eventTypeRegistry.findByCode(OrderCompletedEventType.CODE))
        .thenReturn(Optional.of(new OrderCompletedEventType()));

    notificationEventConsumer.handleDlt(
        notificationEvent, "notification.events.dlt", "timeout after 3 retries");

    verify(notificationMetrics).incrementDlt();
  }

  @Test
  void shouldIncludeEventIdAndOccurredAt_whenBuildingDltPayload() {
    NotificationEvent notificationEvent = NotificationTestData.defaultEvent();
    when(eventTypeRegistry.findByCode(OrderCompletedEventType.CODE))
        .thenReturn(Optional.of(new OrderCompletedEventType()));

    notificationEventConsumer.handleDlt(notificationEvent, "notification.events.dlt", "SMTP Error");

    ArgumentCaptor<FailedNotification> captor = ArgumentCaptor.forClass(FailedNotification.class);

    verify(failedNotificationRepository).save(captor.capture());

    var payload = captor.getValue().getEventPayload();
    assertThat(payload).containsKey("id");
    assertThat(payload).containsKey("occurredAt");
    assertThat(payload.get("id")).isEqualTo(notificationEvent.id());
    assertThat(payload.get("occurredAt")).isEqualTo(notificationEvent.occurredAt().toString());
  }
}
