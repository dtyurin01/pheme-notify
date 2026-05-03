package com.pheme.phemenotify.infrastructure.redis;

import com.pheme.phemenotify.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisDeduplicationAdapterTest extends BaseIntegrationTest {
   @Autowired
   private RedisDeduplicationAdapter redisDeduplicationAdapter;

   @Autowired
   private StringRedisTemplate stringRedisTemplate;

   @AfterEach
   void cleanUp() {
      stringRedisTemplate.getConnectionFactory()
              .getConnection()
              .serverCommands()
              .flushDb();
   }


   @Test
   void shouldReturnTrue_whenEventIsNew() {
       boolean result = redisDeduplicationAdapter.isNew("event1");

       assertThat(result).isTrue();
   }

   @Test
   void shouldReturnFalse_whenEventIsDuplicate() {
      redisDeduplicationAdapter.isNew("event1");

      boolean result = redisDeduplicationAdapter.isNew("event1");

      assertThat(result).isFalse();
   }
}
