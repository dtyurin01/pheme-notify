package com.pheme.phemenotify.infrastructure.metrics;


import com.pheme.phemenotify.persistence.entity.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final Map<Channel, Counter> sentCounters = new EnumMap<>(Channel.class);
    private final Map<Channel, Counter> failedCounters = new EnumMap<>(Channel.class);
    private final Map<Channel, Timer> sendTimers = new EnumMap<>(Channel.class);


    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        for (Channel channel : Channel.values()) {
            sentCounters.put(channel, Counter.builder("notifications.sent")
                    .tag("channel", channel.name()).register(meterRegistry));
            failedCounters.put(channel, Counter.builder("notifications.failed")
                    .tag("channel", channel.name()).register(meterRegistry));
            sendTimers.put(channel, Timer.builder("notification.send.duration")
                .tag("channel", channel.name())
                .publishPercentileHistogram(true)
                .register(meterRegistry));
        }
    }

    public void incrementSent(Channel channel) {
        sentCounters.get(channel).increment();
    }

    public void incrementFailed(Channel channel) {
        failedCounters.get(channel).increment();
    }

    public Timer.Sample sendTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordSendDuration(Timer.Sample sample, Channel channel) {
        sample.stop(sendTimers.get(channel));
    }
}
