package com.pheme.phemenotify.e2e;

import static com.pheme.phemenotify.util.NotificationPersistenceHelper.saveWithDate;
import static com.pheme.phemenotify.util.TestDateUtils.daysAgo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import com.pheme.phemenotify.persistence.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
public class AnalyticsE2ETest extends BaseIntegrationTest {

  @Autowired MockMvc mockMvc;

  @Autowired NotificationRepository notificationRepository;

  @Autowired OrderCompletedEventType orderCompleted;

  @Autowired CacheManager cacheManager;

  @AfterEach
  void cleanup() {
    notificationRepository.deleteAll();
    var cache = cacheManager.getCache("deliveryStats");
    if (cache != null) cache.clear();
  }

  @Test
  void shouldReturnDeliveryStats_whenNotificationsExist() throws Exception {
    // given — 2 DELIVERED, 1 FAILED yesterday
    var yesterday = daysAgo(1);
    saveWithDate(
        notificationRepository,
        orderCompleted,
        yesterday,
        Channel.EMAIL,
        NotificationStatus.DELIVERED,
        "key-1");
    saveWithDate(
        notificationRepository,
        orderCompleted,
        yesterday,
        Channel.EMAIL,
        NotificationStatus.DELIVERED,
        "key-2");
    saveWithDate(
        notificationRepository,
        orderCompleted,
        yesterday,
        Channel.EMAIL,
        NotificationStatus.FAILED,
        "key-3");

    String startDate = LocalDate.now(ZoneOffset.UTC).minusDays(2).toString();
    String endDate = LocalDate.now(ZoneOffset.UTC).toString();

    // when & then
    mockMvc
        .perform(
            get("/api/v1/analytics/delivery-stats")
                .param("startDate", startDate)
                .param("endDate", endDate))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].channel").value("EMAIL"))
        .andExpect(jsonPath("$[0].total").value(3))
        .andExpect(jsonPath("$[0].delivered").value(2))
        .andExpect(jsonPath("$[0].failed").value(1))
        .andExpect(jsonPath("$[0].deliveryRate").value(66.67));
  }

  @Test
  void shouldReturn400_whenStartDateNotBeforeEndDate() throws Exception {
    String date = LocalDate.now(ZoneOffset.UTC).toString();

    mockMvc
        .perform(
            get("/api/v1/analytics/delivery-stats").param("startDate", date).param("endDate", date))
        .andExpect(status().isBadRequest());
  }
}
