package com.pheme.phemenotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheme.phemenotify.infrastructure.metrics.NotificationMetrics;
import com.pheme.phemenotify.infrastructure.redis.RedisDeduplicationAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

  @Mock private RedisDeduplicationAdapter deduplicationAdapter;

  @Mock private NotificationMetrics notificationMetrics;

  @InjectMocks private DeduplicationService deduplicationService;

  @Test
  void shouldReturnTrue_whenEventIsNew() {
    when(deduplicationAdapter.isNew("event-123")).thenReturn(true);

    boolean result = deduplicationService.isNew("event-123");

    assertThat(result).isTrue();
    verify(deduplicationAdapter).isNew("event-123");
  }

  @Test
  void shouldReturnFalse_whenEventIsDuplicate() {
    when(deduplicationAdapter.isNew("event-123")).thenReturn(false);

    boolean result = deduplicationService.isNew("event-123");

    assertThat(result).isFalse();
    verify(notificationMetrics).incrementDuplicateSkipped();
  }
}
