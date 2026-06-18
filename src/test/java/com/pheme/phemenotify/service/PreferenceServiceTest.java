package com.pheme.phemenotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pheme.phemenotify.api.dto.request.CreatePreferenceRequest;
import com.pheme.phemenotify.api.dto.response.PreferenceResponse;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.UserPreferences;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import com.pheme.phemenotify.util.PreferenceTestData;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PreferenceServiceTest {
  @Mock private UserPreferenceRepository userPreferenceRepository;

  @InjectMocks private PreferenceService preferenceService;

  @BeforeEach
  void setUp() {
    lenient().when(userPreferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void shouldReturnPreferenceResponse_whenUserExists() {
    UserPreferences entity =
        PreferenceTestData.entityWith("user-1", Set.of(Channel.EMAIL, Channel.SMS), "en-US", "UTC");

    when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(entity));

    PreferenceResponse response = preferenceService.getByUserId("user-1");

    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.enabledChannels()).containsExactlyInAnyOrder(Channel.EMAIL, Channel.SMS);
    assertThat(response.enabled()).isTrue();
  }

  @Test
  void shouldThrowResourceNotFoundException_whenUserNotFound() {
    when(userPreferenceRepository.findByUserId("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> preferenceService.getByUserId("unknown"))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldCreateNewPreference_whenUserDoesNotExist() {
    CreatePreferenceRequest createPreferenceRequest = PreferenceTestData.defaultRequest();

    when(userPreferenceRepository.findByUserId("new-user")).thenReturn(Optional.empty());

    PreferenceResponse response = preferenceService.upsert("new-user", createPreferenceRequest);

    assertThat(response.userId()).isEqualTo("new-user");
    assertThat(response.enabledChannels()).containsExactlyInAnyOrder(Channel.EMAIL);
    verify(userPreferenceRepository).save(any(UserPreferences.class));
  }

  @Test
  void shouldUpdateEnabledChannels_whenUserExists() {
    UserPreferences existing =
        PreferenceTestData.entityWith("user-1", Set.of(Channel.EMAIL), "en-US", "UTC");
    CreatePreferenceRequest updateRequest =
        new CreatePreferenceRequest(Set.of(Channel.EMAIL, Channel.SMS), null, null, null);

    when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

    PreferenceResponse response = preferenceService.upsert("user-1", updateRequest);

    assertThat(response.userId()).isEqualTo("user-1");
    assertThat(response.enabledChannels()).containsExactlyInAnyOrder(Channel.EMAIL, Channel.SMS);
  }

  @Test
  void shouldNotOverrideLocale_whenLocaleIsNull() {
    UserPreferences existing =
        PreferenceTestData.entityWith("user-1", Set.of(Channel.EMAIL), "fr", "UTC");
    CreatePreferenceRequest updateRequest =
        new CreatePreferenceRequest(Set.of(Channel.SMS), null, null, null);

    when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

    PreferenceResponse response = preferenceService.upsert("user-1", updateRequest);

    assertThat(response.locale()).isEqualTo("fr");
  }

  @Test
  void shouldNotOverrideTimezone_whenTimezoneIsNull() {
    UserPreferences existing =
        PreferenceTestData.entityWith("user-1", Set.of(Channel.EMAIL), "en", "Europe/Kiev");
    CreatePreferenceRequest request = new CreatePreferenceRequest(Set.of(Channel.SMS), null, null, null);
    when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

    PreferenceResponse response = preferenceService.upsert("user-1", request);

    assertThat(response.timezone()).isEqualTo("Europe/Kiev");
  }

  @Test
  void shouldUpdateLocaleAndTimezone_whenBothProvided() {
    UserPreferences existing =
        PreferenceTestData.entityWith("user-1", Set.of(Channel.EMAIL), "en", "UTC");
    CreatePreferenceRequest request =
        new CreatePreferenceRequest(Set.of(Channel.EMAIL), "de", "Europe/Berlin", null);
    when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

    PreferenceResponse response = preferenceService.upsert("user-1", request);

    assertThat(response.locale()).isEqualTo("de");
    assertThat(response.timezone()).isEqualTo("Europe/Berlin");
  }

  @Test
  void shouldUpdateEnabledField_whenUpsertWithEnabledFalse() {
    UserPreferences existing =
        PreferenceTestData.entityWith("user-1", Set.of(Channel.EMAIL), "en", "UTC");
    CreatePreferenceRequest request =
        new CreatePreferenceRequest(Set.of(Channel.EMAIL), null, null, false);

    when(userPreferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));

    PreferenceResponse response = preferenceService.upsert("user-1", request);

    assertThat(response.enabled()).isFalse();
    assertThat(existing.isEnabled()).isFalse();
  }
}
