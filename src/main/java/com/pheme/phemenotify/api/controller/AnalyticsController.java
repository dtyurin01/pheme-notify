package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.dto.response.DeliveryStatsResponse;
import com.pheme.phemenotify.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Analytics", description = "Notification delivery statistics")
@RestController
@RequestMapping(ApiPaths.V1 + "/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  @Operation(
      summary = "Get delivery stats",
      description =
          "Returns daily delivery statistics per channel and event type, including rolling 7-day"
              + " average delivery rate")
  @GetMapping("/delivery-stats")
  public ResponseEntity<List<DeliveryStatsResponse>> getDeliveryStats(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
    return ResponseEntity.ok(analyticsService.getDeliveryStats(startDate, endDate));
  }
}
