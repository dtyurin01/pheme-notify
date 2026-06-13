package com.pheme.phemenotify.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import com.pheme.phemenotify.util.NotificationTestData;
import com.pheme.phemenotify.util.PreferenceTestData;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

public class LoadSmokeE2ETest extends BaseIntegrationTest {

  private static final int EVENT_COUNT = 1000;
  private static final int SMS_LIMIT =
      3; // application.yaml: notification.rate-limit.sms.max-requests

  @Autowired UserPreferenceRepository userPreferenceRepository;

  @Autowired KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  @Autowired NotificationRepository notificationRepository;

  @AfterEach
  void cleanup() {
    notificationRepository.deleteAll();
  }

  @Test
  void shouldProcessAllEvents_withoutLoss_whenBurstOf1000Sent() {
    userPreferenceRepository.save(
        PreferenceTestData.entityWith("user-load", Set.of(Channel.SMS), "en", "UTC"));

    for (int i = 0; i < EVENT_COUNT; i++) {
      NotificationEvent event =
          NotificationTestData.eventFor(
              "load-" + i, "user-load", Map.of("orderId", String.valueOf(i), "amount", "10"));
      kafkaTemplate.send("notification.events", event.id(), event);
    }

    await()
        .atMost(2, TimeUnit.MINUTES)
        .untilAsserted(
            () -> {
              var all = notificationRepository.findAll();

              assertThat(all).hasSize(EVENT_COUNT);
              assertThat(all).noneMatch(n -> n.getStatus() == NotificationStatus.PENDING);

              long delivered =
                  all.stream().filter(n -> n.getStatus() == NotificationStatus.DELIVERED).count();
              long rateLimited =
                  all.stream()
                      .filter(n -> "RATE_LIMIT_EXCEEDED".equals(n.getErrorMessage()))
                      .count();

              assertThat(delivered).isEqualTo(SMS_LIMIT);
              assertThat(rateLimited).isEqualTo(EVENT_COUNT - SMS_LIMIT);
            });
  }
}
