package com.pheme.phemenotify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "notification.rate-limit")
public class RateLimitProperties {

    private ChannelLimit email = new ChannelLimit(5, 1);
    private ChannelLimit sms   = new ChannelLimit(3, 1);
    private ChannelLimit push  = new ChannelLimit(20, 1);

    @Getter
    @Setter
    public static class ChannelLimit {

        private int maxRequests;
        private int windowHours;

        public ChannelLimit(int maxRequests, int windowHours) {
            this.maxRequests = maxRequests;
            this.windowHours = windowHours;
        }

        public long windowMillis() {
            return Duration.ofHours(windowHours).toMillis();
        }
    }
}
