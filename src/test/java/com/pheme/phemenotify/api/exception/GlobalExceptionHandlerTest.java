package com.pheme.phemenotify.api.exception;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pheme.phemenotify.api.controller.AnalyticsController;
import com.pheme.phemenotify.api.controller.PreferenceController;
import com.pheme.phemenotify.service.AnalyticsService;
import com.pheme.phemenotify.service.PreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({PreferenceController.class, AnalyticsController.class})
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PreferenceService preferenceService;

  @MockitoBean private AnalyticsService analyticsService;

  @Test
  void shouldReturn404WithProblemDetail_whenResourceNotFound() throws Exception {
    when(preferenceService.getByUserId("unknown"))
        .thenThrow(new ResourceNotFoundException("User not found"));

    mockMvc
        .perform(get("/api/v1/users/unknown/preferences"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/not-found"))
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.detail").value("User not found"));
  }

  @Test
  void shouldReturn429WithProblemDetail_whenRateLimitExceeded() throws Exception {
    when(preferenceService.getByUserId("user-1"))
        .thenThrow(new RateLimitExceededException("Email limit: 5/hour exceeded for user user-1"));

    mockMvc
        .perform(get("/api/v1/users/user-1/preferences"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/rate-limit-exceeded"))
        .andExpect(jsonPath("$.title").value("Rate Limit Exceeded"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.detail").value("Email limit: 5/hour exceeded for user user-1"));
  }

  @Test
  void shouldReturn500WithoutStackTrace_whenUnexpectedError() throws Exception {
    when(preferenceService.getByUserId("user-1"))
        .thenThrow(new RuntimeException("DB connection lost"));

    mockMvc
        .perform(get("/api/v1/users/user-1/preferences"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/internal-error"))
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.detail").value(not(containsString("DB connection lost"))))
        .andExpect(jsonPath("$.trace").doesNotExist())
        .andExpect(jsonPath("$.stackTrace").doesNotExist())
        .andExpect(jsonPath("$.errorId").exists());
  }

  @Test
  void shouldReturn400WithErrorsArray_whenValidationFails() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/user-1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabledChannels\":[],\"locale\":\"en\",\"timezone\":\"UTC\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/validation-failed"))
        .andExpect(jsonPath("$.title").value("Validation Failed"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[*]", hasItem(containsString("enabledChannels"))))
        .andExpect(jsonPath("$.detail", containsString("enabledChannels")));
  }

  @Test
  void shouldReturn400_whenRequiredParamMissing() throws Exception {
    mockMvc
        .perform(get("/api/v1/analytics/delivery-stats").param("endDate", "2026-06-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/validation-failed"))
        .andExpect(jsonPath("$.title").value("Validation Failed"));
  }

  @Test
  void shouldReturn400_whenParamTypeMismatch() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/analytics/delivery-stats")
                .param("startDate", "not-a-date")
                .param("endDate", "2026-06-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/validation-failed"))
        .andExpect(jsonPath("$.title").value("Validation Failed"))
        .andExpect(jsonPath("$.detail").value("startDate: invalid value 'not-a-date'"));
  }

  @Test
  void shouldReturn400_whenInvalidDateRange() throws Exception {
    when(analyticsService.getDeliveryStats(
            java.time.LocalDate.parse("2026-06-10"), java.time.LocalDate.parse("2026-06-01")))
        .thenThrow(new InvalidDateRangeException("startDate must be before endDate"));

    mockMvc
        .perform(
            get("/api/v1/analytics/delivery-stats")
                .param("startDate", "2026-06-10")
                .param("endDate", "2026-06-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("https://pheme.com/errors/invalid-date-range"))
        .andExpect(jsonPath("$.title").value("Invalid Date Range"))
        .andExpect(jsonPath("$.detail").value("startDate must be before endDate"));
  }

  @Test
  void shouldReturnErrorIdAndNoLeak_whenUnexpectedExceptionOnAnalytics() throws Exception {
    when(analyticsService.getDeliveryStats(
            java.time.LocalDate.parse("2026-06-01"), java.time.LocalDate.parse("2026-06-10")))
        .thenThrow(new RuntimeException("DB connection lost"));

    mockMvc
        .perform(
            get("/api/v1/analytics/delivery-stats")
                .param("startDate", "2026-06-01")
                .param("endDate", "2026-06-10"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
        .andExpect(jsonPath("$.detail").value(not(containsString("DB connection lost"))))
        .andExpect(jsonPath("$.trace").doesNotExist())
        .andExpect(jsonPath("$.stackTrace").doesNotExist())
        .andExpect(jsonPath("$.errorId").exists())
        .andExpect(content().string(not(containsString("DB connection lost"))));
  }
}
