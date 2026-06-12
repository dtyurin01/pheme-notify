package com.pheme.phemenotify.infrastructure.redis;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisDeduplicationAdapter {

  private static final String KEY_PREFIX = "dedup:";
  private static final Duration TTL = Duration.ofHours(24); // make config?

  private final StringRedisTemplate stringRedisTemplate;

  public boolean isNew(String eventId) {
    Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + eventId, "1", TTL);

    return Boolean.TRUE.equals(result);
  }
}
