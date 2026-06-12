package com.pheme.phemenotify.persistence.entity.eventtype;

import com.pheme.phemenotify.persistence.entity.EventType;
import org.springframework.stereotype.Component;

@Component
public class PaymentFailedEventType implements EventType {

  public static final String CODE = "PAYMENT_FAILED";

  @Override
  public String getCode() {
    return CODE;
  }

  @Override
  public String getDescription() {
    return "Alert sent to user when their payment transaction fails";
  }
}
