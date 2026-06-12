package com.pheme.phemenotify.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pheme.phemenotify.api.exception.RateLimitExceededException;
import com.pheme.phemenotify.config.RateLimitProperties;
import com.pheme.phemenotify.infrastructure.redis.RedisRateLimitAdapter;
import com.pheme.phemenotify.persistence.entity.Channel;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RateLimitServiceTest {

  @Mock private RedisRateLimitAdapter rateLimitAdapter;

  @Mock private RateLimitProperties properties;

  @InjectMocks private RateLimitService rateLimitService;

  @BeforeEach
  void setUp() {
    RateLimitProperties.ChannelLimit emailLimit =
        new RateLimitProperties.ChannelLimit(5, Duration.ofHours(1));
    RateLimitProperties.ChannelLimit smsLimit =
        new RateLimitProperties.ChannelLimit(3, Duration.ofHours(1));
    RateLimitProperties.ChannelLimit pushLimit =
        new RateLimitProperties.ChannelLimit(20, Duration.ofHours(1));

    lenient().when(properties.getEmail()).thenReturn(emailLimit);
    lenient().when(properties.getSms()).thenReturn(smsLimit);
    lenient().when(properties.getPush()).thenReturn(pushLimit);
  }

  @Test
  void shouldNotThrow_whenAllowed() {
    when(rateLimitAdapter.isAllowed(any(), any(), anyLong(), anyInt())).thenReturn(true);

    assertThatCode(() -> rateLimitService.checkLimit("user-1", Channel.EMAIL))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldThrowRateLimitExceededException_whenNotAllowed() {
    when(rateLimitAdapter.isAllowed(any(), any(), anyLong(), anyInt())).thenReturn(false);

    assertThatThrownBy(() -> rateLimitService.checkLimit("user-1", Channel.EMAIL))
        .isInstanceOf(RateLimitExceededException.class)
        .hasMessageContaining("user-1");
  }

  @Test
  void shouldPassCorrectLimits_whenEmailChannel() {
    when(rateLimitAdapter.isAllowed(any(), any(), anyLong(), anyInt())).thenReturn(true);

    rateLimitService.checkLimit("user-1", Channel.EMAIL);

    verify(rateLimitAdapter).isAllowed("user-1", "EMAIL", 3_600_000L, 5);
  }

  @Test
  void shouldPassCorrectLimits_whenSmsChannel() {
    when(rateLimitAdapter.isAllowed(any(), any(), anyLong(), anyInt())).thenReturn(true);

    rateLimitService.checkLimit("user-1", Channel.SMS);

    verify(rateLimitAdapter).isAllowed("user-1", "SMS", 3_600_000L, 3);
  }

  @Test
  void shouldPassCorrectLimits_whenPushChannel() {
    when(rateLimitAdapter.isAllowed(any(), any(), anyLong(), anyInt())).thenReturn(true);

    rateLimitService.checkLimit("user-1", Channel.PUSH);

    verify(rateLimitAdapter).isAllowed("user-1", "PUSH", 3_600_000L, 20);
  }
}
