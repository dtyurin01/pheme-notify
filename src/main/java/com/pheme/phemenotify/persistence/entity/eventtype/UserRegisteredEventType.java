
package com.pheme.phemenotify.persistence.entity.eventtype;

import org.springframework.stereotype.Component;
import com.pheme.phemenotify.persistence.entity.EventType;

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
