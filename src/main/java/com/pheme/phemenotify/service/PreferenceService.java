package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.dto.request.CreatePreferenceRequest;
import com.pheme.phemenotify.api.dto.response.PreferenceResponse;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.persistence.entity.UserPreferences;
import com.pheme.phemenotify.persistence.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PreferenceService {
  private final UserPreferenceRepository repository;

  @Transactional(readOnly = true)
  public PreferenceResponse getByUserId(String userId) {
    return repository
        .findByUserId(userId)
        .map(this::toResponse)
        .orElseThrow(
            () -> new ResourceNotFoundException("Preferences not found for user: " + userId));
  }

  @Transactional
  public PreferenceResponse upsert(String userId, CreatePreferenceRequest createPreferenceRequest) {
    UserPreferences userPreferences =
        repository
            .findByUserId(userId)
            .orElseGet(() -> UserPreferences.builder().userId(userId).build());

    userPreferences.setEnabledChannels(createPreferenceRequest.enabledChannels());

    if (createPreferenceRequest.locale() != null) {
      userPreferences.setLocale(createPreferenceRequest.locale());
    }
    if (createPreferenceRequest.timezone() != null) {
      userPreferences.setTimezone(createPreferenceRequest.timezone());
    }
    if (createPreferenceRequest.enabled() != null) {
      userPreferences.setEnabled(createPreferenceRequest.enabled());
    }

    return toResponse(repository.save(userPreferences));
  }

  private PreferenceResponse toResponse(UserPreferences entity) {
    return new PreferenceResponse(
        entity.getUserId(),
        entity.getEnabledChannels(),
        entity.getLocale(),
        entity.getTimezone(),
        entity.isEnabled());
  }
}
