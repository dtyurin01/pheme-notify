package com.pheme.phemenotify.e2e;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
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
        stringRedisTemplate.delete("dedup:event-1");
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
}
