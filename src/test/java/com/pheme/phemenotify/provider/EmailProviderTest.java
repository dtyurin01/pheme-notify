package com.pheme.phemenotify.provider;


import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.util.NotificationTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
public class EmailProviderTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailProvider emailProvider;

    @Test
    void shouldThrowException_whenEmailNotInPayload(){
        NotificationEvent notificationEvent = NotificationTestData.eventWithPayload(Map.of());

        assertThatThrownBy(() -> emailProvider.send(notificationEvent, "template"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("user-1");
    }
}
