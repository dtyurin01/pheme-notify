package com.pheme.phemenotify.provider;

import com.pheme.phemenotify.messaging.event.NotificationEvent;

public interface NotificationProvider {

    void send(NotificationEvent event, String renderedTemplate);
}
