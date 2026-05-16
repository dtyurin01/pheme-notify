package com.pheme.phemenotify.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.pheme.phemenotify.messaging.event.EventTypeDeserializer;
import com.pheme.phemenotify.persistence.entity.EventType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class JacksonConfig {
    @Bean
    public Module eventTypeModule(EventTypeDeserializer deserializer) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(EventType.class, deserializer);
        return module;
    }
}