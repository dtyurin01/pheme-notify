package com.pheme.phemenotify.provider;

import com.pheme.phemenotify.messaging.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@RequiredArgsConstructor
public class EmailProvider implements NotificationProvider {

    private final JavaMailSender mailSender;

    @Override
    public void send(NotificationEvent event, String renderedTemplate) {

        String email = event.payload().get("email");
        if (email == null) {
            throw new IllegalArgumentException("Email not found in payload for user: " + event.userId());
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Notification: " +
                event.eventType().getCode());
        message.setText(renderedTemplate);

        mailSender.send(message);
        log.info("Email sent to user {}", event.userId());
    }
}

