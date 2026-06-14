package com.pheme.phemenotify.api.dto.response;

import com.pheme.phemenotify.persistence.entity.Channel;
import java.util.Set;

public record PreferenceResponse(
    String userId, Set<Channel> enabledChannels, String locale, String timezone, boolean enabled) {}
