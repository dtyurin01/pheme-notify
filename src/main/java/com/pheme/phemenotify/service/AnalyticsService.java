package com.pheme.phemenotify.service;

import com.pheme.phemenotify.api.dto.response.DeliveryStatsResponse;
import com.pheme.phemenotify.api.exception.InvalidDateRangeException;
import com.pheme.phemenotify.persistence.projection.DeliveryStatsProjection;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final NotificationRepository notificationRepository;

    @Cacheable(value = "deliveryStats", key = "#startDate + ':' + #endDate")
    public List<DeliveryStatsResponse> getDeliveryStats(LocalDate startDate, LocalDate endDate) {
        if (!startDate.isBefore(endDate)) {
            throw new InvalidDateRangeException("startDate must be before endDate");
        }

        Instant start = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<DeliveryStatsResponse> result = notificationRepository
            .findDeliveryStats(start, end)
            .stream()
            .map(this::toResponse)
            .toList();

        log.debug("Loaded delivery stats from DB rows={}", result.size());
        return result;
    }

    private DeliveryStatsResponse toResponse(DeliveryStatsProjection p) {
        return new DeliveryStatsResponse(
            p.getChannel(),
            p.getEventType(),
            p.getDay(),
            p.getTotal(),
            p.getDelivered(),
            p.getFailed(),
            p.getDeliveryRate(),
            p.getRollingWeeklyAvg()
        );
    }
}