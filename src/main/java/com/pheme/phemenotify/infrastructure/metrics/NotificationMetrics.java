package com.pheme.phemenotify.infrastructure.metrics;

import com.pheme.phemenotify.persistence.entity.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

  private final MeterRegistry meterRegistry;
  private final Map<Channel, Counter> sentCounters = new EnumMap<>(Channel.class);
  private final Map<Channel, Counter> failedCounters = new EnumMap<>(Channel.class);
  private final Map<Channel, Timer> sendTimers = new EnumMap<>(Channel.class);
  private final Map<Channel, Counter> rateLimitExceededCounters = new EnumMap<>(Channel.class);
  private final Counter dltCounter;
  private final Counter duplicateSkippedCounter;
  private final Counter retrySuccessCounter;
  private final Counter retryFailedCounter;
  private final Counter retryExhaustedCounter;

  public NotificationMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    for (Channel channel : Channel.values()) {
      sentCounters.put(
          channel,
          Counter.builder("notifications.sent")
              .tag("channel", channel.name())
              .register(meterRegistry));
      failedCounters.put(
          channel,
          Counter.builder("notifications.failed")
              .tag("channel", channel.name())
              .register(meterRegistry));
      sendTimers.put(
          channel,
          Timer.builder("notification.send.duration")
              .tag("channel", channel.name())
              .publishPercentileHistogram(true)
              .register(meterRegistry));
      rateLimitExceededCounters.put(
          channel,
          Counter.builder("notifications.ratelimit.exceeded")
              .tag("channel", channel.name())
              .register(meterRegistry));
    }
    dltCounter = Counter.builder("notifications.dlt").register(meterRegistry);
    duplicateSkippedCounter =
        Counter.builder("notifications.duplicate.skipped").register(meterRegistry);
    retrySuccessCounter = Counter.builder("notifications.retry.success").register(meterRegistry);
    retryFailedCounter = Counter.builder("notifications.retry.failed").register(meterRegistry);
    retryExhaustedCounter =
        Counter.builder("notifications.retry.exhausted").register(meterRegistry);
  }

  public void incrementDlt() {
    dltCounter.increment();
  }

  public void incrementDuplicateSkipped() {
    duplicateSkippedCounter.increment();
  }

  public void incrementRetrySuccess() {
    retrySuccessCounter.increment();
  }

  public void incrementRetryFailed() {
    retryFailedCounter.increment();
  }

  public void incrementRetryExhausted() {
    retryExhaustedCounter.increment();
  }

  public void incrementRateLimitExceeded(Channel channel) {
    rateLimitExceededCounters.get(channel).increment();
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
