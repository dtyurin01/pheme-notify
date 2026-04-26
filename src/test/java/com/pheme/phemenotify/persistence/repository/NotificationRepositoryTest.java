package com.pheme.phemenotify.persistence.repository;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Optional;

public class NotificationRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrderCompletedEventType eventType;

    @Test
    @DisplayName("Should save & find notifications by ID")
    void shouldFindById_whenSaved(){
        Notification n = createBaseNotification("unique-key-123");
        Notification saved = notificationRepository.save(n);
        Optional<Notification> found = notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("user-123");
        assertThat(found.get().getEventType()).isEqualTo(eventType);
    }

    @Test
    @DisplayName("Should return Empty, when key not found")
    void shouldReturnEmpty_whenKeyNotFound(){
        Optional<Notification> found = notificationRepository.findByIdempotencyKey("non-existent-key");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByIdempotencyKey_whenSaved(){
        Notification n = createBaseNotification("unique-key-456");
        notificationRepository.save(n);
        Optional<Notification> found = notificationRepository.findByIdempotencyKey("unique-key-456");

        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo("unique-key-456");
        assertThat(found.get().getUserId()).isEqualTo("user-123");
        assertThat(found.get().getEventType()).isEqualTo(eventType);
    }

    @AfterEach
    void cleanup() {
        notificationRepository.deleteAll();
    }

    private Notification createBaseNotification(String idempotencyKey) {
        return Notification.builder()
                .userId("user-123")
                .channel(Channel.EMAIL)
                .eventType(eventType)
                .idempotencyKey(idempotencyKey)
                .status(NotificationStatus.PENDING)
                .build();
    }
}
