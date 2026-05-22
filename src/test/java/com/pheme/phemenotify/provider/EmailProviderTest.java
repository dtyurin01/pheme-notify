package com.pheme.phemenotify.provider;


import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.util.NoOpCircuitBreakerFactory;
import com.pheme.phemenotify.util.NotificationTestData;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class EmailProviderTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailProvider emailProvider;

    @BeforeEach
    void setUp() {
        emailProvider = new EmailProvider(mailSender, new NoOpCircuitBreakerFactory());
    }

    @Test
    void shouldSendEmail_whenValidPayload() {
        NotificationEvent event = NotificationTestData
                .eventWithPayload(Map.of("email", "user@example.com"));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        emailProvider.send(event, "<h1>Hello!</h1>");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldPropagateException_whenCircuitBreakerFallbackTriggered() {
        NotificationEvent event = NotificationTestData
                .eventWithPayload(Map.of("email", "user@example.com"));
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
        doThrow(new RuntimeException("SMTP down"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailProvider.send(event, "<h1>Hello!</h1>"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email service unavailable");
    }



    @Test
    void shouldThrowIllegalArgument_whenEmailMissingInPayload() {
        NotificationEvent event = NotificationTestData.eventWithPayload(Map.of());

        assertThatThrownBy(() -> emailProvider.send(event, "template"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-1");
    }

}
