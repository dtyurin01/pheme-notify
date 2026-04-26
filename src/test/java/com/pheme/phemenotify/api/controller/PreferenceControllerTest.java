package com.pheme.phemenotify.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pheme.phemenotify.api.dto.request.CreatePreferenceRequest;
import com.pheme.phemenotify.api.dto.response.PreferenceResponse;
import com.pheme.phemenotify.api.exception.ResourceNotFoundException;
import com.pheme.phemenotify.service.PreferenceService;
import com.pheme.phemenotify.util.PreferenceTestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Set;

import static org.mockito.Mockito.when;

@WebMvcTest(PreferenceController.class)
public class PreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private PreferenceService preferenceService;


    @Test
    void shouldReturn200WithPreferenceResponse_whenUserExists() throws Exception {
        PreferenceResponse response = PreferenceTestData.defaultResponse();
        when(preferenceService.getByUserId("user-1")).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/user-1/preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void shouldReturn404_whenUserNotFound() throws Exception {
        when(preferenceService.getByUserId("unknown")).thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/unknown/preferences"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    @Test
    void shouldReturn200WithUpdatedReferences_whenRequestIsValid() throws Exception {
        CreatePreferenceRequest request = PreferenceTestData.defaultRequest();
        PreferenceResponse response = PreferenceTestData.defaultResponse();

        when(preferenceService.upsert(eq("user-1"), any()))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/users/user-1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("user-1"))
        .andExpect(jsonPath("$.enabled").value(true));
    }
    @Test
    void shouldReturn400_whenEnabledChannelIsEmpty() throws Exception {
        CreatePreferenceRequest request = new CreatePreferenceRequest(Set.of(), "en", "UTC");

        mockMvc.perform(put("/api/v1/users/user-1/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Failed"));
    }
}