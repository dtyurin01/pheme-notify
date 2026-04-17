package com.pheme.phemenotify.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name("notification.events").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationEventsDltTopic() {
        return TopicBuilder.name("notification.events.dlt").partitions(1).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler errorHandler() {
        var handler = new DefaultErrorHandler(new FixedBackOff(1000L, 2L));

        handler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                org.springframework.kafka.support.converter.ConversionException.class);
        return handler;
    }
}
