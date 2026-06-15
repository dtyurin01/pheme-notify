package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.dto.response.NotificationResponse;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "Notification delivery status")
@RestController
@RequestMapping(ApiPaths.V1 + "/notifications")
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationService notificationService;

  @Operation(
      summary = "Get notification status",
      description = "Returns delivery status for given notification id")
  @GetMapping(path = "/{id}/status", version = "1")
  public ResponseEntity<NotificationResponse> getStatus(@PathVariable UUID id) {
    return ResponseEntity.ok(notificationService.getById(id));
  }

  @Operation(
      summary = "Get notification status by event id and channel",
      description = "Returns delivery status for given Kafka event id and channel")
  @GetMapping(path = "/status", version = "1")
  public ResponseEntity<NotificationResponse> getStatusByEventIdAndChannel(
      @RequestParam String eventId, @RequestParam Channel channelId) {
    return ResponseEntity.ok(notificationService.getByEventIdAndChannel(channelId, eventId));
  }
}
