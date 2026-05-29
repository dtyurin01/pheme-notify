package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.dto.response.NotificationResponse;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import com.pheme.phemenotify.util.NotificationTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void shouldReturnNotificationResponse_whenNotificationExists() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(NotificationTestData.defaultEntity(id)));

        NotificationResponse response = notificationService.getById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.channel()).isEqualTo(Channel.EMAIL);
        assertThat(response.eventType()).isEqualTo(OrderCompletedEventType.CODE);
        assertThat(response.status()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void shouldThrowResourceNotFoundException_whenNotificationNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.getById(id))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining(id.toString());
    }
}
