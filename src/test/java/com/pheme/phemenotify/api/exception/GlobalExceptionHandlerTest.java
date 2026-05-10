package com.pheme.phemenotify.api.exception;

import com.pheme.phemenotify.api.controller.PreferenceController;
import com.pheme.phemenotify.service.PreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PreferenceController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;


    @MockitoBean
    private PreferenceService preferenceService;

    @Test
    void shouldReturn404WithProblemDetail_whenResourceNotFound() throws Exception {
        when(preferenceService.getByUserId("unknown"))
                .thenThrow(new ResourceNotFoundException("User not found"));

        mockMvc.perform(get("/api/v1/users/unknown/preferences"))
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

        mockMvc.perform(get("/api/v1/users/user-1/preferences"))
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

        mockMvc.perform(get("/api/v1/users/user-1/preferences"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("https://pheme.com/errors/internal-error"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
