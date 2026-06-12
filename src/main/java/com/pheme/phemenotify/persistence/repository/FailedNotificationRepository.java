package com.pheme.phemenotify.persistence.repository;

import com.pheme.phemenotify.persistence.entity.FailedNotification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailedNotificationRepository extends JpaRepository<FailedNotification, UUID> {

  // Used in FailedNotificationRetryScheduler:
  // takes pending messages with next_retry_at
  List<FailedNotification> findByStatusAndNextRetryAtBefore(NotificationStatus status, Instant now);
}
