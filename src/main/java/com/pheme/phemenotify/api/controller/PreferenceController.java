package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.dto.request.CreatePreferenceRequest;
import com.pheme.phemenotify.api.dto.response.PreferenceResponse;
import com.pheme.phemenotify.service.PreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Preferences", description = "User notification channel preferences")
@RestController
@RequestMapping(ApiPaths.V1 + "/users")
@RequiredArgsConstructor
public class PreferenceController {

  private final PreferenceService preferenceService;

  @Operation(summary = "Get user preferences", description = "Returns notification settings for given user")
  @GetMapping("/{userId}/preferences")
  public ResponseEntity<PreferenceResponse> getPreferences(@PathVariable String userId) {
    return ResponseEntity.ok(preferenceService.getByUserId(userId));
  }

  @Operation(
      summary = "Create or update user preferences",
      description = "Upserts enabled channels, locale, timezone and enabled flag for given user")
  @PutMapping("/{userId}/preferences")
  public ResponseEntity<PreferenceResponse> upsertPreferences(
      @PathVariable String userId, @Valid @RequestBody CreatePreferenceRequest request) {
    return ResponseEntity.ok(preferenceService.upsert(userId, request));
  }
}
