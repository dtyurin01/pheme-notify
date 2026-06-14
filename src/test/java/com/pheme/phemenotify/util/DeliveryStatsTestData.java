package com.pheme.phemenotify.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pheme.phemenotify.api.dto.response.DeliveryStatsResponse;
import com.pheme.phemenotify.persistence.projection.DeliveryStatsProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class DeliveryStatsTestData {

  private DeliveryStatsTestData() {}

  public static final LocalDate DEFAULT_DATE = LocalDate.now(ZoneOffset.UTC).minusDays(7);

  static final String CHANNEL = "EMAIL";
  static final String EVENT_TYPE = "order.completed";
  static final long TOTAL = 10L;
  static final long DELIVERED = 8L;
  static final long FAILED = 2L;
  static final BigDecimal DELIVERY_RATE = new BigDecimal("80.00");
  static final BigDecimal ROLLING_WEEKLY_AVG = new BigDecimal("75.00");

  public static DeliveryStatsProjection mockProjection() {
    return mockProjection(DEFAULT_DATE);
  }

  public static DeliveryStatsProjection mockProjection(LocalDate date) {
    DeliveryStatsProjection p = mock(DeliveryStatsProjection.class);
    when(p.getChannel()).thenReturn(CHANNEL);
    when(p.getEventType()).thenReturn(EVENT_TYPE);
    when(p.getDay()).thenReturn(date);
    when(p.getTotal()).thenReturn(TOTAL);
    when(p.getDelivered()).thenReturn(DELIVERED);
    when(p.getFailed()).thenReturn(FAILED);
    when(p.getDeliveryRate()).thenReturn(DELIVERY_RATE);
    when(p.getRollingWeeklyAvg()).thenReturn(ROLLING_WEEKLY_AVG);
    return p;
  }

  public static DeliveryStatsResponse mockResponse() {
    return mockResponse(DEFAULT_DATE);
  }

  public static DeliveryStatsResponse mockResponse(LocalDate date) {
    return new DeliveryStatsResponse(
        CHANNEL, EVENT_TYPE, date, TOTAL, DELIVERED, FAILED, DELIVERY_RATE, ROLLING_WEEKLY_AVG);
  }
}
