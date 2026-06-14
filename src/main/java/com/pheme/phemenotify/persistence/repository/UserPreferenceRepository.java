package com.pheme.phemenotify.persistence.repository;

import com.pheme.phemenotify.persistence.entity.UserPreferences;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreferences, UUID> {

  Optional<UserPreferences> findByUserId(String userId);
}
