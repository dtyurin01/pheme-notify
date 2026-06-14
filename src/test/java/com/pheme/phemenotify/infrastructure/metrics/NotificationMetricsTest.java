package com.pheme.phemenotify.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheme.phemenotify.persistence.entity.Channel;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NotificationMetricsTest {
  private SimpleMeterRegistry registry;
  private NotificationMetrics notificationMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    notificationMetrics = new NotificationMetrics(registry);
  }

  @Test
  void shouldIncrementSentCounter_whenIncrementSentCalled() {
    notificationMetrics.incrementSent(Channel.EMAIL);

    double count = registry.get("notifications.sent").tag("channel", "EMAIL").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  void shouldIncrementFailedCounter_whenIncrementFailedCalled() {
    notificationMetrics.incrementFailed(Channel.SMS);

    double count = registry.get("notifications.failed").tag("channel", "SMS").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  void shouldNotAffectOtherChannels_whenIncrementingOneChannel() {
    notificationMetrics.incrementSent(Channel.EMAIL);

    double smsCount = registry.get("notifications.sent").tag("channel", "SMS").counter().count();

    assertThat(smsCount).isEqualTo(0.0);
  }

  @Test
  void shouldIncrementDltCounter_whenIncrementDltCalled() {
    notificationMetrics.incrementDlt();

    double count = registry.get("notifications.dlt").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  void shouldIncrementDuplicateSkippedCounter_whenIncrementDuplicateSkippedCalled() {
    notificationMetrics.incrementDuplicateSkipped();

    double count = registry.get("notifications.duplicate.skipped").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  void shouldIncrementRateLimitExceededCounter_whenIncrementRateLimitExceededCalled() {
    notificationMetrics.incrementRateLimitExceeded(Channel.SMS);

    double count =
        registry.get("notifications.ratelimit.exceeded").tag("channel", "SMS").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  void shouldIncrementRetryCounters_whenRetryMethodsCalled() {
    notificationMetrics.incrementRetrySuccess();
    notificationMetrics.incrementRetryFailed();
    notificationMetrics.incrementRetryExhausted();

    assertThat(registry.get("notifications.retry.success").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("notifications.retry.failed").counter().count()).isEqualTo(1.0);
    assertThat(registry.get("notifications.retry.exhausted").counter().count()).isEqualTo(1.0);
  }

  @Test
  void shouldRecordSendDuration_whenRecordSendDurationCalled() {
    Timer.Sample sample = notificationMetrics.sendTimer();

    // Simulate some processing time
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    notificationMetrics.recordSendDuration(sample, Channel.PUSH);

    Timer timer = registry.get("notification.send.duration").tag("channel", "PUSH").timer();

    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(100.0);
  }
}
