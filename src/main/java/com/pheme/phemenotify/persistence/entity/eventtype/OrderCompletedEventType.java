package com.pheme.phemenotify.persistence.entity.eventtype;

import com.pheme.phemenotify.persistence.entity.EventType;
import org.springframework.stereotype.Component;

@Component
public class OrderCompletedEventType implements EventType {

  public static final String CODE = "ORDER_COMPLETED";

  @Override
  public String getCode() {
    return CODE;
  }

  @Override
  public String getDescription() {
    return "Event that occurs when an order is completed.";
  }
}
