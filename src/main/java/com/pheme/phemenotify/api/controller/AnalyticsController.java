package com.pheme.phemenotify.api.controller;

import com.pheme.phemenotify.api.ApiPaths;
import com.pheme.phemenotify.api.dto.response.DeliveryStatsResponse;
import com.pheme.phemenotify.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping(ApiPaths.V1 + "/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/delivery-stats")
    public ResponseEntity<List<DeliveryStatsResponse>> getDeliveryStats(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(analyticsService.getDeliveryStats(startDate, endDate));
    }
}
