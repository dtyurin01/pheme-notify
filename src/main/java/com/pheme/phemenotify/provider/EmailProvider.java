package com.pheme.phemenotify.provider;

import com.pheme.phemenotify.messaging.event.NotificationEvent;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class EmailProvider implements NotificationProvider {

    private final JavaMailSender mailSender;
    private final CircuitBreaker circuitBreaker;

    public EmailProvider(JavaMailSender mailSender, CircuitBreakerFactory<?, ?> factory) {
        this.mailSender = mailSender;
        this.circuitBreaker = factory.create("email");
    }

    @Override
    public void send(NotificationEvent event, String renderedTemplate) {
        String email = event.payload().get("email");
        if (email == null) {
            throw new IllegalArgumentException("Email not found in payload for user: " + event.userId());
        }

        circuitBreaker.run(
            () -> {
                try {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                    helper.setTo(email);
                    helper.setSubject("Notification: " + event.eventType().getCode());
                    helper.setText(renderedTemplate, true); // true = HTML
                    mailSender.send(message);
                    log.info("Email sent to user {}", event.userId());
                    return null;
                } catch (MessagingException | MailException e) {
                    throw new RuntimeException("Failed to build email message", e);
                }
            },
            throwable -> {
                if (throwable instanceof CallNotPermittedException) {
                    log.error("Circuit breaker OPEN for email, userId={}", event.userId());
                } else {
                    log.error("Failed to send email, userId={}", event.userId(), throwable);
                }
                throw new RuntimeException("Email service unavailable", throwable);
            }
        );
    }
}
