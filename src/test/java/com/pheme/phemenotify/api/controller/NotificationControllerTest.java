package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.service.NotificationService;
import com.pheme.phemenotify.util.NotificationTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void shouldReturn200WithNotificationResponse_whenNotificationExists() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.getById(id)).thenReturn(NotificationTestData.defaultResponse(id));

        mockMvc.perform(get("/api/v1/notifications/{id}/status", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void shouldReturn404_whenNotificationNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.getById(id))
                .thenThrow(new ResourceNotFoundException("Notification not found: " + id));

        mockMvc.perform(get("/api/v1/notifications/{id}/status", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }
}
