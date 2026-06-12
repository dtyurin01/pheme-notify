package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.dto.request.CreatePreferenceRequest;
import com.pheme.phemenotify.api.dto.response.PreferenceResponse;
import com.pheme.phemenotify.service.PreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ApiPaths.V1 + "/users")
@RequiredArgsConstructor
public class PreferenceController {

  private final PreferenceService preferenceService;

  @GetMapping("/{userId}/preferences")
  public ResponseEntity<PreferenceResponse> getPreferences(@PathVariable String userId) {
    return ResponseEntity.ok(preferenceService.getByUserId(userId));
  }

  @PutMapping("/{userId}/preferences")
  public ResponseEntity<PreferenceResponse> upsertPreferences(
      @PathVariable String userId, @Valid @RequestBody CreatePreferenceRequest request) {
    return ResponseEntity.ok(preferenceService.upsert(userId, request));
  }
}
