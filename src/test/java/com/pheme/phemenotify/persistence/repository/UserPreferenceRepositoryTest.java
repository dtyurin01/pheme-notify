package com.pheme.phemenotify.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.persistence.entity.UserPreferences;
import com.pheme.phemenotify.util.PreferenceTestData;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserPreferenceRepositoryTest extends BaseIntegrationTest {

  @Autowired private UserPreferenceRepository userPreferenceRepository;

  @AfterEach
  void cleanUp() {
    userPreferenceRepository.deleteAll();
  }

  @Test
  void shouldFindByUserId_whenSaved() {
    userPreferenceRepository.save(PreferenceTestData.defaultEntity());

    Optional<UserPreferences> found = userPreferenceRepository.findByUserId("user-1");

    assertThat(found).isPresent();
    assertThat(found.get().getUserId()).isEqualTo("user-1");
  }

  @Test
  void shouldReturnEmpty_whenUserIdNotFound() {
    Optional<UserPreferences> found = userPreferenceRepository.findByUserId("non-existent-user");

    assertThat(found).isEmpty();
  }
}
