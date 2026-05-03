package com.pheme.phemenotify.util;

import com.pheme.phemenotify.api.dto.request.CreatePreferenceRequest;
import com.pheme.phemenotify.api.dto.response.PreferenceResponse;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.UserPreferences;

import java.util.Set;

public class PreferenceTestData {

    public static UserPreferences defaultEntity() {
        return UserPreferences.builder()
                .userId("user-1")
                .enabledChannels(Set.of(Channel.EMAIL))
                .locale("en")
                .timezone("UTC")
                .enabled(true)
                .build();
    }

    public static UserPreferences entityWith(String userId, Set<Channel> channels, String locale, String timezone) {
        return UserPreferences.builder()
                .userId(userId)
                .enabledChannels(channels)
                .locale(locale)
                .timezone(timezone)
                .enabled(true)
                .build();
    }

    public static UserPreferences entityWithNoChannels() {
        return UserPreferences.builder()
                .userId("user-1")
                .enabledChannels(Set.of())
                .locale("en")
                .timezone("UTC")
                .enabled(true)
                .build();
    }

    public static CreatePreferenceRequest defaultRequest() {
        return new CreatePreferenceRequest(Set.of(Channel.EMAIL), "en", "UTC");
    }

    public static PreferenceResponse defaultResponse() {
        return new PreferenceResponse("user-1", Set.of(Channel.EMAIL), "en", "UTC", true);
    }
}
