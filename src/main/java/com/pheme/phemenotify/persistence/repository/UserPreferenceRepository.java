package com.pheme.phemenotify.persistence.repository;

import com.pheme.phemenotify.persistence.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository extends JpaRepository<UserPreferences, UUID> {

    Optional<UserPreferences> findByUserId(String userId);
}
