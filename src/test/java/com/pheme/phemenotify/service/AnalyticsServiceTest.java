package com.pheme.phemenotify.service;

import static com.pheme.phemenotify.util.DeliveryStatsTestData.DEFAULT_DATE;
import static com.pheme.phemenotify.util.DeliveryStatsTestData.mockProjection;
import static com.pheme.phemenotify.util.DeliveryStatsTestData.mockResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheme.phemenotify.api.dto.response.DeliveryStatsResponse;
import com.pheme.phemenotify.persistence.projection.DeliveryStatsProjection;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock private NotificationRepository notificationRepository;

  @InjectMocks private AnalyticsService analyticsService;

  private static final LocalDate START = DEFAULT_DATE;
  private static final LocalDate END = LocalDate.now(ZoneOffset.UTC);

  @Test
  void shouldReturnFromDb_whenCalled() {
    DeliveryStatsProjection projection = mockProjection();
    when(notificationRepository.findDeliveryStats(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(projection));

    List<DeliveryStatsResponse> result = analyticsService.getDeliveryStats(START, END);

    assertThat(result).hasSize(1);
    verify(notificationRepository).findDeliveryStats(any(), any());
  }

  @Test
  void shouldReturnEmptyList_whenDbReturnsEmpty() {
    when(notificationRepository.findDeliveryStats(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of());

    List<DeliveryStatsResponse> result = analyticsService.getDeliveryStats(START, END);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldMapAllProjectionFields_toResponse() {
    DeliveryStatsProjection projection = mockProjection();
    when(notificationRepository.findDeliveryStats(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(projection));

    List<DeliveryStatsResponse> result = analyticsService.getDeliveryStats(START, END);

    DeliveryStatsResponse expected = mockResponse();
    DeliveryStatsResponse actual = result.get(0);
    assertThat(actual.channel()).isEqualTo(expected.channel());
    assertThat(actual.eventType()).isEqualTo(expected.eventType());
    assertThat(actual.date()).isEqualTo(expected.date());
    assertThat(actual.total()).isEqualTo(expected.total());
    assertThat(actual.delivered()).isEqualTo(expected.delivered());
    assertThat(actual.failed()).isEqualTo(expected.failed());
    assertThat(actual.deliveryRate()).isEqualByComparingTo(expected.deliveryRate());
    assertThat(actual.rollingWeeklyAvg()).isEqualByComparingTo(expected.rollingWeeklyAvg());
  }

  @Test
  void shouldPassCorrectInstants_toRepository() {
    when(notificationRepository.findDeliveryStats(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of());

    analyticsService.getDeliveryStats(START, END);

    Instant expectedStart = START.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant expectedEnd = END.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    verify(notificationRepository).findDeliveryStats(eq(expectedStart), eq(expectedEnd));
  }

  @Test
  void shouldReturnMultipleRows_preservingOrder() {
    LocalDate otherDate = DEFAULT_DATE.minusDays(1);
    DeliveryStatsProjection first = mockProjection();
    DeliveryStatsProjection second = mockProjection(otherDate);
    when(notificationRepository.findDeliveryStats(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(first, second));

    List<DeliveryStatsResponse> result = analyticsService.getDeliveryStats(START, END);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).date()).isEqualTo(DEFAULT_DATE);
    assertThat(result.get(1).date()).isEqualTo(otherDate);
  }
}
