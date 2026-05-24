package com.pheme.phemenotify.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.converter.ConversionException;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final int MAIN_TOPIC_PARTITIONS = 3;
    private static final int DLT_TOPIC_PARTITIONS = 1;
    private static final int TOPIC_REPLICAS = 1;
    private static final long ERROR_HANDLER_BACKOFF_MS = 1000L;
    private static final long ERROR_HANDLER_MAX_ATTEMPTS = 2L;

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name("notification.events")
                .partitions(MAIN_TOPIC_PARTITIONS)
                .replicas(TOPIC_REPLICAS)
                .build();
    }

    @Bean
    public NewTopic notificationEventsDltTopic() {
        return TopicBuilder.name("notification.events.dlt")
                .partitions(DLT_TOPIC_PARTITIONS)
                .replicas(TOPIC_REPLICAS)
                .build();
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        var handler = new DefaultErrorHandler(
                new FixedBackOff(ERROR_HANDLER_BACKOFF_MS, ERROR_HANDLER_MAX_ATTEMPTS));

        handler.addNotRetryableExceptions(SerializationException.class, ConversionException.class);
        return handler;
    }
}
