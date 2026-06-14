package com.pheme.phemenotify.persistence.entity.eventtype;

import com.pheme.phemenotify.persistence.entity.EventType;
import org.springframework.stereotype.Component;

@Component
public class UserRegisteredEventType implements EventType {

  public static final String CODE = "USER_REGISTERED";

  @Override
  public String getCode() {
    return CODE;
  }

  @Override
  public String getDescription() {
    return "Welcome notification for newly registered users";
  }
}
