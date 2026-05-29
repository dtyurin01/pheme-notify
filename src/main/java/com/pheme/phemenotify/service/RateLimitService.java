package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.exception.RateLimitExceededException;
import com.pheme.phemenotify.config.RateLimitProperties;
import com.pheme.phemenotify.infrastructure.redis.RedisRateLimitAdapter;
import com.pheme.phemenotify.persistence.entity.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final RedisRateLimitAdapter rateLimitAdapter;
    private final RateLimitProperties properties;

    public void checkLimit(String userId, Channel channel) {
        RateLimitProperties.ChannelLimit limit = getLimit(channel);

        boolean allowed = rateLimitAdapter.isAllowed(
            userId,
            channel.name(),
            limit.window().toMillis(),
            limit.maxRequests()
        );

        if (!allowed) {
            throw new RateLimitExceededException(
                "Rate limit exceeded for user " + userId +
                    ": max " + limit.maxRequests() +
                    " " + channel.name().toLowerCase() +
                    " notifications per " + limit.window()
            );
        }
    }


    private RateLimitProperties.ChannelLimit getLimit(Channel channel) {
        return switch (channel) {
            case EMAIL -> properties.getEmail();
            case SMS -> properties.getSms();
            case PUSH -> properties.getPush();
        };
    }
}
