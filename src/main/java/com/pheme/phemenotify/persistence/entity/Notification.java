package com.pheme.phemenotify.persistence.entity;

import jakarta.persistence.*;
import com.pheme.phemenotify.persistence.converter.EventTypeAttributeConverter;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name="user_id", nullable=false)
    private String userId;

    @Column(name = "event_type", nullable = false)
    @Convert(converter = EventTypeAttributeConverter.class)
    private EventType eventType;

    @Convert(disableConversion = true)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "channel", nullable = false)
    private Channel channel;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Builder.Default
    @Convert(disableConversion = true)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "provider_response", columnDefinition = "TEXT")
    private String providerResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

}
