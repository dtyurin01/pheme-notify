package com.pheme.phemenotify.e2e;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import com.pheme.phemenotify.util.NotificationTestData;
import com.pheme.phemenotify.util.PreferenceTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class NotificationPipelineE2ETest  extends BaseIntegrationTest {

    @Autowired
    KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserPreferenceRepository userPreferenceRepository;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    public void setUp() {
        notificationRepository.deleteAll();
        userPreferenceRepository.deleteAll();
        var dedupKeys = stringRedisTemplate.keys("dedup:*");
        if (dedupKeys != null && !dedupKeys.isEmpty()) {
            stringRedisTemplate.delete(dedupKeys);
        }
    }

    @Test
    void shouldDeliverEmailNotification_whenValidEventReceived(){

        // given
        userPreferenceRepository.save(
            PreferenceTestData.defaultEntity());

        NotificationEvent event =
            NotificationTestData.defaultEvent();

        // when
        kafkaTemplate.send(
            "notification.events", event.id(), event);

        // then
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var found =
                    notificationRepository.findByIdempotencyKey("event-1:EMAIL");
                assertThat(found).isPresent();
                assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.DELIVERED);
                assertThat(found.get().getSentAt()).isNotNull();
        });

        assertThat(stringRedisTemplate.hasKey("dedup:event-1")).isTrue();
    }

    @Test
    void shouldDeliverToAllEnabledChannels_whenUserHasMultipleChannels(){
        // given
        userPreferenceRepository.save(PreferenceTestData.entityWith(
            "user-2", Set.of(Channel.EMAIL, Channel.SMS), "en", "UTC"));

        NotificationEvent event = NotificationTestData.eventFor(
            "evt-h2", "user-2",
            Map.of("email", "user2@example.com", "orderId", "42",
            "amount", "100"));

        // when
        kafkaTemplate.send(
            "notification.events", event.id(), event);

        // then
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var notifications = notificationRepository.findAll();
                assertThat(notifications).hasSize(2);
                assertThat(notifications)
                    .allMatch(n -> n.getStatus() == NotificationStatus.DELIVERED);
                assertThat(notifications)
                    .extracting(Notification::getChannel)
                    .containsExactlyInAnyOrder(Channel.EMAIL, Channel.SMS);
            });
    }

    @Test
    void shouldSkipDuplicateEvent_whenSaveEventIdReceived()
        throws InterruptedException{
        // given
        userPreferenceRepository.save(PreferenceTestData.entityWith(
            "user-3",Set.of(Channel.EMAIL), "en", "UTC"));

        NotificationEvent event = NotificationTestData.eventFor(
            "evt-d1", "user-3",
            Map.of("email", "user3@example.com", "orderId", "99", "amount", "50"));

        // when - same event id sent twice

        kafkaTemplate.send(
            "notification.events", event.id(), event);
        Thread.sleep(100);
        kafkaTemplate.send(
            "notification.events", event.id(), event);

        // then
        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                var notifications = notificationRepository.findAll();
                assertThat(notifications).hasSize(1);
                assertThat(notifications.getFirst()
                    .getStatus()).isEqualTo(NotificationStatus.DELIVERED);
            });
    }

    @Test
    void shouldSkipProcessing_whenUserHasNoPreferences(){
        NotificationEvent event = NotificationTestData.eventFor(
            "evt-p1", "user-4",
            Map.of("email", "user-4@example.com", "orderId", "1",
                "amount", "10")
        );

        kafkaTemplate.send(
            "notification.events", event.id(), event);

        await().atMost(30, TimeUnit.SECONDS)
            .untilAsserted(() -> {
                assertThat(notificationRepository.findAll()).isEmpty();
            });

        assertThat(stringRedisTemplate.hasKey("dedup:evt-p1")).isTrue();
    }


}
