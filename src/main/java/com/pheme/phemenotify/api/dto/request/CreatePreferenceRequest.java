package com.pheme.phemenotify.api.dto.request;

import com.pheme.phemenotify.persistence.entity.Channel;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record CreatePreferenceRequest(
    @NotEmpty Set<Channel> enabledChannels, String locale, String timezone, Boolean enabled) {}
