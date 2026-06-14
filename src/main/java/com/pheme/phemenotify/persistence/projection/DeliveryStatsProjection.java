package com.pheme.phemenotify.persistence.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DeliveryStatsProjection {

  String getChannel();

  String getEventType(); // event_type → getEventType()

  LocalDate getDay(); // DATE_TRUNC('day', ...)

  Long getTotal();

  Long getDelivered();

  Long getFailed();

  BigDecimal getDeliveryRate(); // ROUND(..., 2) → BigDecimal

  BigDecimal getRollingWeeklyAvg();
}
