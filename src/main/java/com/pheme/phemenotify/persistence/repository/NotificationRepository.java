package com.pheme.phemenotify.persistence.repository;

import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.projection.DeliveryStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Transactional
    @Query(value="UPDATE notifications SET created_at = :createdAt WHERE id = :id", nativeQuery = true)
    void updateCreatedAt(@Param("id") UUID id, @Param("createdAt") Instant createdAt);
    @Query(value = """
            WITH daily_stats AS (
                SELECT
                    channel, event_type,
                    DATE_TRUNC('day', created_at) AS day,
                    COUNT(*) AS total,
                    COUNT(*) FILTER (WHERE status = 'DELIVERED') AS delivered,
                    COUNT(*) FILTER (WHERE status = 'FAILED')    AS failed,
                    ROUND(COUNT(*) FILTER (WHERE status = 'DELIVERED') * 100.0 / COUNT(*), 2) AS delivery_rate
                FROM notifications
                WHERE created_at >= :startDate AND created_at < :endDate
                GROUP BY channel, event_type, DATE_TRUNC('day', created_at)
            )
            SELECT channel, event_type, day, total, delivered, failed, delivery_rate,
                ROUND(AVG(delivery_rate) OVER (
                    PARTITION BY channel ORDER BY day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
                ), 2) AS rolling_weekly_avg
            FROM daily_stats
            ORDER BY day DESC, channel
            """, nativeQuery = true)
    List<DeliveryStatsProjection> findDeliveryStats(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
}
