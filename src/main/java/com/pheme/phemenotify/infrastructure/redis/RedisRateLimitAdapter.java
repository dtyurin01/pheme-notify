package com.pheme.phemenotify.infrastructure.redis;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimitAdapter {

    private static final String KEY_PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public boolean isAllowed(String userId,
                             String channel,
                             long windowMillis,
                             int maxRequests){

        String key = KEY_PREFIX + userId + ":" + channel;
        long now = System.currentTimeMillis();

        Long result = redisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowMillis),
                String.valueOf(maxRequests)
        );

        boolean allowed = result != null && result == 0L;

        if(!allowed){
            log.warn("Rate limit exceeded for user {} on channel {}, window {} ms, max {} requests",
                    userId, channel, windowMillis, maxRequests);
        }

        return allowed;
    }
}
