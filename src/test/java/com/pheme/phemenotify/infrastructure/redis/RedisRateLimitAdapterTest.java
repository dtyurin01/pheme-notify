package com.pheme.phemenotify.infrastructure.redis;

import com.pheme.phemenotify.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisRateLimitAdapterTest extends BaseIntegrationTest {

    @Autowired
    private RedisRateLimitAdapter rateLimitAdapter;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final long WINDOW_MS = 3_600_000L;
    private static final int MAX = 5;

    @AfterEach
    void cleanUp() {
        stringRedisTemplate.getConnectionFactory()
            .getConnection()
            .serverCommands()
            .flushDb();
    }

    @Test
    void shouldAllow_whenFirstRequest() {
        boolean allowed = rateLimitAdapter.isAllowed(
            "user-1", "EMAIL", WINDOW_MS, MAX);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllow_whenUnderLimit() {
        for (int i = 0; i < 4; i++) {
            boolean allowed = rateLimitAdapter.isAllowed(
                "user-1", "EMAIL", WINDOW_MS, MAX);
            assertThat(allowed).isTrue();
        }
    }

    @Test
    void shouldBlock_whenLimitReached() {
        for (int i = 0; i < MAX; i++) {
            rateLimitAdapter.isAllowed(
                "user-1", "EMAIL", WINDOW_MS, MAX);
        }

        boolean sixthRequest = rateLimitAdapter.isAllowed(
            "user-1", "EMAIL", WINDOW_MS, MAX);

        assertThat(sixthRequest).isFalse();
    }

    @Test
    void shouldIsolate_whenDifferentUsers() {
        for (int i = 0; i < MAX; i++) {
            rateLimitAdapter.isAllowed(
                "user-1", "EMAIL", WINDOW_MS, MAX);
        }
        boolean user2Allowed = rateLimitAdapter.isAllowed(
            "user-2", "EMAIL", WINDOW_MS, MAX);

        assertThat(user2Allowed).isTrue();
    }

    @Test
    void shouldIsolate_whenDifferentChannels() {
        for (int i = 0; i < MAX; i++) {
            rateLimitAdapter.isAllowed(
                "user-1", "EMAIL", WINDOW_MS, MAX);
        }

        boolean smsAllowed = rateLimitAdapter.isAllowed(
            "user-1", "SMS", WINDOW_MS, MAX);

        assertThat(smsAllowed).isTrue();
    }

    @Test
    void shouldAllow_whenWindowExpired() throws InterruptedException {
        long shortWindow = 200L; // 200ms
        long sleepMs = shortWindow * 3;

        for (int i = 0; i < MAX; i++) {
            rateLimitAdapter.isAllowed(
                "user-1", "EMAIL", shortWindow, MAX);
        }
        assertThat(rateLimitAdapter.isAllowed(
            "user-1", "EMAIL", shortWindow, MAX)).isFalse();

        Thread.sleep(sleepMs); // wait for window to expire

        boolean allowed = rateLimitAdapter.isAllowed(
            "user-1", "EMAIL", shortWindow, MAX);

        assertThat(allowed).isTrue();
    }

    @Test
    void shouldAllowExactlyMaxRequests_whenBurst() {
        int allowed = 0;
        for (int i = 0; i < 100; i++) {
            if (rateLimitAdapter.isAllowed("user-1", "EMAIL", WINDOW_MS, MAX)) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(MAX); // 5 in this case
    }
}
