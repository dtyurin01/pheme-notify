package com.pheme.phemenotify.messaging.consumer;


import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.repository.FailedNotificationRepository;
import com.pheme.phemenotify.service.NotificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventConsumer {

    private final NotificationOrchestrator orchestrator;
    private final FailedNotificationRepository failedNotificationRepository;


    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 5000, multiplier = 2),
            dltTopicSuffix = ".dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @KafkaListener(topics = "notification.events", groupId = "notification-hub")
    public void handleEvent(NotificationEvent
                                    event) {
        log.info("Received event: id:{}, userId:{}", event.id(), event.userId());

        orchestrator.process(event);
    }

    @DltHandler
    public void handleDlt(NotificationEvent event, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {

        log.error("Event failed after all retries: id:{}, topic={}", event.id(), topic);

        FailedNotification failed = FailedNotification.builder()
                .userId(event.userId())
                .channel(event.channel())
                .eventType(event.eventType())
                .eventPayload(buildEventPayload(event))
                .errorMessage(errorMessage)
                .build();

        failedNotificationRepository.save(failed);
    }

    private Map<String, Object> buildEventPayload(NotificationEvent event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", event.id());
        payload.put("userId", event.userId());
        payload.put("eventType", event.eventType().getCode());
        payload.put("channel", event.channel().name());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("payload", event.payload());
        return payload;
    }

}
