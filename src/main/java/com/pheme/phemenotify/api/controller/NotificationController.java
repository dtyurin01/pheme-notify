package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.dto.response.NotificationResponse;
import com.pheme.phemenotify.service.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationService notificationService;

  @GetMapping("/{id}/status")
  public ResponseEntity<NotificationResponse> getStatus(@PathVariable UUID id) {
    return ResponseEntity.ok(notificationService.getById(id));
  }
}
