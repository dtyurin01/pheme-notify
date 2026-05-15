package com.pheme.phemenotify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.retry-scheduler")
public class RetrySchedulerProperties {

    private int maxAttempts = 3;
    private long backoffBaseSeconds = 300;
    private long fixedDelayMs = 60_000;
}
