package com.pheme.phemenotify.api.dto.response;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DeliveryStatsResponse(
    String channel,
    String eventType,
    LocalDate date,
    long total,
    long delivered,
    long failed,
    BigDecimal deliveryRate,
    BigDecimal rollingWeeklyAvg)
    implements Serializable {}
