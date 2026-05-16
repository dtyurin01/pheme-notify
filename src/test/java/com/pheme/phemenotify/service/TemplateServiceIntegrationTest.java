package com.pheme.phemenotify.service;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import com.pheme.phemenotify.persistence.entity.eventtype.UserRegisteredEventType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class TemplateServiceIntegrationTest {
    @Autowired
    private TemplateService templateService;

    @Test
    void shouldRenderEmailTemplate_withVariables(){
        String result = templateService.render(
                new OrderCompletedEventType(),
                Channel.EMAIL,
                Map.of("orderId", "123", "amount", "10")
        );
        log.info("=== RENDERED EMAIL ===\n{}", result);
        assertThat(result).contains("123");
        assertThat(result).contains("10");
    }

    @Test
    void shouldRenderSmsTemplate_withVariables(){
        String result = templateService.render(
                new UserRegisteredEventType(),
                Channel.SMS,
                Map.of("email", "user@example.com")
        );
        log.info("=== RENDERED SMS ===\n{}", result);
        assertThat(result).contains("user@example.com");
    }
}
