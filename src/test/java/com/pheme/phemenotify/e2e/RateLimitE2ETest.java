package com.pheme.phemenotify.e2e;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

public class RateLimitE2ETest extends BaseIntegrationTest {

  @Autowired KafkaTemplate<String, NotificationEvent> kafkaTemplate;

  @Autowired NotificationRepository notificationRepository;

  @Autowired UserPreferenceRepository userPreferenceRepository;

  @Autowired StringRedisTemplate stringRedisTemplate;

  @BeforeEach
  void setUp() {
    notificationRepository.deleteAll();
    userPreferenceRepository.deleteAll();
    var dedupKeys = stringRedisTemplate.keys("dedup:*");
    if (dedupKeys != null && !dedupKeys.isEmpty()) {
      stringRedisTemplate.delete(dedupKeys);
    }
    var rateLimitKeys = stringRedisTemplate.keys("ratelimit:*");
    if (rateLimitKeys != null && !rateLimitKeys.isEmpty()) {
      stringRedisTemplate.delete(rateLimitKeys);
    }
  }

  @Test
  void shouldRespectRateLimit_whenBurstOfEventsReceived() {
    // given - email limit is 5/hour
    userPreferenceRepository.save(
        PreferenceTestData.entityWith("user-rl", Set.of(Channel.EMAIL), "en", "UTC"));

    // when - 7 event, different ids
    for (int i = 1; i <= 7; i++) {
      NotificationEvent event =
          NotificationTestData.eventFor(
              "evt-rl-" + i,
              "user-rl",
              Map.of("email", "user-rl@example.com", "orderId", String.valueOf(i), "amount", "10"));
      kafkaTemplate.send("notification.events", event.id(), event);
    }

    // then - exactly 5 DELIVERED, 2 FAILED with RATE_LIMIT_EXCEEDED

    await()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var notifications = notificationRepository.findAll();
              assertThat(notifications).hasSize(7);

              long delivered =
                  notifications.stream()
                      .filter(n -> n.getStatus() == NotificationStatus.DELIVERED)
                      .count();

              long failed =
                  notifications.stream()
                      .filter(n -> n.getStatus() == NotificationStatus.FAILED)
                      .count();

              assertThat(delivered).isEqualTo(5L);
              assertThat(failed).isEqualTo(2L);
            });
  }
}
