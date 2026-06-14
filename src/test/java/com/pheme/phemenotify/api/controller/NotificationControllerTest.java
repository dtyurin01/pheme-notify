package com.pheme.phemenotify.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.service.NotificationService;
import com.pheme.phemenotify.util.NotificationTestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificationService notificationService;

  private static final String URL = ApiPaths.V1 + "/notifications/{id}/status";

  private static final String STATUS_BY_EVENT_URL = ApiPaths.V1 + "/notifications/status";

  @Test
  void shouldReturn200WithNotificationResponse_whenNotificationExists() throws Exception {
    UUID id = UUID.randomUUID();
    when(notificationService.getById(id)).thenReturn(NotificationTestData.defaultResponse(id));

    mockMvc
        .perform(get(URL, id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.errorMessage").value((Object) null));
  }

  @Test
  void shouldReturn404_whenNotificationNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(notificationService.getById(id))
        .thenThrow(new ResourceNotFoundException("Notification not found: " + id));

    mockMvc
        .perform(get(URL, id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"));
  }

  @Test
  void shouldReturn500WithoutLeakingDetails_whenUnexpectedError() throws Exception {
    UUID id = UUID.randomUUID();
    when(notificationService.getById(id))
        .thenThrow(new RuntimeException("secret db password 1234"));

    mockMvc
        .perform(get(URL, id))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }

  @Test
  void shouldReturn200WithNotificationResponse_whenByEventIdAndChannel() throws Exception {
    UUID id = UUID.randomUUID();
    when(notificationService.getByEventIdAndChannel(Channel.EMAIL, "event-1"))
        .thenReturn(NotificationTestData.defaultResponse(id));

    mockMvc
        .perform(get(STATUS_BY_EVENT_URL).param("eventId", "event-1").param("channelId", "EMAIL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.errorMessage").value((Object) null));
  }

  void shouldReturn404_whenNotFoundByEventIdAndChannel() throws Exception {
    when(notificationService.getByEventIdAndChannel(Channel.EMAIL, "event-1"))
        .thenThrow(
            new ResourceNotFoundException(
                "Notification not found for eventId: event-1 and channel: EMAIL"));

    mockMvc
        .perform(get(STATUS_BY_EVENT_URL).param("eventId", "event-1").param("channel", "EMAIL"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"));
  }
}
