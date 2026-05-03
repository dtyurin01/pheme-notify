package com.pheme.phemenotify.service;

import com.pheme.phemenotify.infrastructure.redis.RedisDeduplicationAdapter;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.UserPreferences;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import com.pheme.phemenotify.provider.NotificationProvider;
import com.pheme.phemenotify.provider.ProviderRegistry;
import com.pheme.phemenotify.util.NotificationTestData;
import com.pheme.phemenotify.util.PreferenceTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOrchestratorTest {

    @Mock
    private RedisDeduplicationAdapter deduplicationAdapter;
    @Mock
    private UserPreferenceRepository userPreferenceRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ProviderRegistry providerRegistry;
    @Mock
    private NotificationProvider emailProvider;
    @Mock
    private NotificationProvider smsProvider;

    @InjectMocks
    private NotificationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        lenient().when(deduplicationAdapter.isNew("event-1")).thenReturn(true);
        lenient().when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void shouldSkip_whenEventIsDuplicate() {
        when(deduplicationAdapter.isNew("event-1")).thenReturn(false);

        orchestrator.process(NotificationTestData.defaultEvent());

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void shouldSkip_whenUserPreferencesNotFound() {
        when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        orchestrator.process(NotificationTestData.defaultEvent());

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void shouldSkip_whenEnabledChannelsEmpty() {
        when(userPreferenceRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(PreferenceTestData.entityWithNoChannels()));

        orchestrator.process(NotificationTestData.defaultEvent());

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void shouldDeliverNotification_whenAllSuccess() {
        when(userPreferenceRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(PreferenceTestData.defaultEntity()));
        when(providerRegistry.getProvider(Channel.EMAIL)).thenReturn(emailProvider);

        orchestrator.process(NotificationTestData.defaultEvent());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void shouldMarkAsFailed_whenProviderThrows() {
        when(userPreferenceRepository.findByUserId("user-1"))
                .thenReturn(Optional.of(PreferenceTestData.defaultEntity()));
        when(providerRegistry.getProvider(Channel.EMAIL)).thenReturn(emailProvider);
        doThrow(new RuntimeException("SMTP error")).when(emailProvider).send(any(), any());

        orchestrator.process(NotificationTestData.defaultEvent());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getAllValues().get(1).getErrorMessage()).isEqualTo("SMTP error");
    }

    @Test
    void shouldContinueOtherChannels_whenOneChannelFails() {
        UserPreferences prefs = PreferenceTestData.entityWith(
                "user-1", Set.of(Channel.EMAIL, Channel.SMS), "en", "UTC");

        when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(prefs));
        when(providerRegistry.getProvider(Channel.EMAIL)).thenReturn(emailProvider);
        when(providerRegistry.getProvider(Channel.SMS)).thenReturn(smsProvider);
        doThrow(new RuntimeException("SMTP error")).when(emailProvider).send(any(), any());

        orchestrator.process(NotificationTestData.defaultEvent());

        verify(notificationRepository, times(4)).save(any());
        verify(emailProvider).send(any(), any());
        verify(smsProvider).send(any(), any());
    }
}
