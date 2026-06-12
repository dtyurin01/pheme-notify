package com.pheme.phemenotify.persistence.entity;

import com.pheme.phemenotify.persistence.converter.EventTypeAttributeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "failed_notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedNotification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id")
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Convert(disableConversion = true)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "channel", nullable = false)
  private Channel channel;

  @Column(name = "event_type", nullable = false)
  @Convert(converter = EventTypeAttributeConverter.class)
  private EventType eventType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "event_payload", columnDefinition = "jsonb", nullable = false)
  private Map<String, Object> eventPayload;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Builder.Default
  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Builder.Default
  @Convert(disableConversion = true)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private NotificationStatus status = NotificationStatus.PENDING;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_retry_at")
  private Instant lastRetryAt;

  @Builder.Default
  @Column(name = "next_retry_at", nullable = false)
  private Instant nextRetryAt = Instant.now();
}
