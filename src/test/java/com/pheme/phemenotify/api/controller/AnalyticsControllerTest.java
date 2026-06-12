package com.pheme.phemenotify.api.controller;

import static com.pheme.phemenotify.util.DeliveryStatsTestData.DEFAULT_DATE;
import static com.pheme.phemenotify.util.DeliveryStatsTestData.mockResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.exception.InvalidDateRangeException;
import com.pheme.phemenotify.service.AnalyticsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AnalyticsService analyticsService;

  private static final String DELIVERY_STATS_URL = ApiPaths.V1 + "/analytics/delivery-stats";
  private static final String START = DEFAULT_DATE.toString();
  private static final String END = DEFAULT_DATE.plusDays(7).toString();

  @Test
  void shouldReturn200WithStats_whenValidDates() throws Exception {
    when(analyticsService.getDeliveryStats(any(), any())).thenReturn(List.of(mockResponse()));

    mockMvc
        .perform(get(DELIVERY_STATS_URL).param("startDate", START).param("endDate", END))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$[0].eventType").value("order.completed"))
        .andExpect(jsonPath("$[0].total").value(10));
  }

  @Test
  void shouldReturn200WithEmptyList_whenNoData() throws Exception {
    when(analyticsService.getDeliveryStats(any(), any())).thenReturn(List.of());

    mockMvc
        .perform(get(DELIVERY_STATS_URL).param("startDate", START).param("endDate", END))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void shouldReturn400_whenStartDateAfterEndDate() throws Exception {
    when(analyticsService.getDeliveryStats(any(), any()))
        .thenThrow(new InvalidDateRangeException("startDate must be before endDate"));

    mockMvc
        .perform(get(DELIVERY_STATS_URL).param("startDate", END).param("endDate", START))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid Date Range"));
  }

  @Test
  void shouldReturn400_whenDateParamMissing() throws Exception {
    mockMvc
        .perform(get(DELIVERY_STATS_URL).param("startDate", START))
        .andExpect(status().isBadRequest());

    verify(analyticsService, never()).getDeliveryStats(any(), any());
  }

  @Test
  void shouldReturn400_whenDateFormatInvalid() throws Exception {
    mockMvc
        .perform(get(DELIVERY_STATS_URL).param("startDate", "not-a-date").param("endDate", END))
        .andExpect(status().isBadRequest());

    verify(analyticsService, never()).getDeliveryStats(any(), any());
  }
}
