package com.pheme.phemenotify.provider;

import com.pheme.phemenotify.messaging.event.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsProvider implements NotificationProvider {
    @Override
    public void send(NotificationEvent event, String renderedTemplate) {
        log.info("SMS Sent to user {}: {}", event.userId(), renderedTemplate);
    }
}
