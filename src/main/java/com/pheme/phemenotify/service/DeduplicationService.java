package com.pheme.phemenotify.service;

import com.pheme.phemenotify.infrastructure.metrics.NotificationMetrics;
import com.pheme.phemenotify.infrastructure.redis.RedisDeduplicationAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

  private final RedisDeduplicationAdapter deduplicationAdapter;
  private final NotificationMetrics notificationMetrics;

  public boolean isNew(String eventId) {
    boolean isNew = deduplicationAdapter.isNew(eventId);
    if (!isNew) {
      log.debug("Duplicate event detected, skipping: eventId={}", eventId);
      notificationMetrics.incrementDuplicateSkipped();
    }
    return isNew;
  }
}
