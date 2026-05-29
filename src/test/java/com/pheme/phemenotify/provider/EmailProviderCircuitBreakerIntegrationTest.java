package com.pheme.phemenotify.provider;


import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.messaging.event.NotificationEvent;
import com.pheme.phemenotify.util.NotificationTestData;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "management.health.mail.enabled=false")
public class EmailProviderCircuitBreakerIntegrationTest extends BaseIntegrationTest {

    private static final int SLIDING_WINDOW_SIZE = 10;
    private static final int PERMITTED_CALLS_IN_HALF_OPEN = 3;
    private static final int FAILURE_RATE_THRESHOLD_PCT = 50; // 50%
    @Autowired
    private EmailProvider emailProvider;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockitoBean
    private JavaMailSender mailSender;

    private NotificationEvent event;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.find("email").ifPresent(CircuitBreaker::reset);
        event = NotificationTestData.eventWithPayload(Map.of("email", "user@example.com"));
        when(mailSender.createMimeMessage())
            .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
    }

    //  ─────────── helpers ───────────

    private void makeFailingCalls(int count) {
        doThrow(new RuntimeException("SMTP down"))
            .when(mailSender).send(any(MimeMessage.class));

        for (int i = 0; i < count; i++) {
            try {
                emailProvider.send(event, "<h1>Hello!</h1>");
            } catch (RuntimeException ignored) {
                // Ignored - we're testing circuit breaker behavior
            }
        }
    }

    private void makeSuccessfulCalls(int count) {
        doNothing().when(mailSender).send(any(MimeMessage.class));

        for (int i = 0; i < count; i++) {
            emailProvider.send(event, "<h1>Hello!</h1>");
        }
    }

    private void openCircuitBreaker() {
        makeFailingCalls(SLIDING_WINDOW_SIZE);
    }

    private void transitionToHalfOpen() {
        circuitBreakerRegistry.circuitBreaker("email").transitionToHalfOpenState();
    }

    private void stubMailSenderWithFailures(int failCount) {
        doAnswer(new Answer<Void>() {
            int callCount = 0;

            @Override
            public Void answer(InvocationOnMock invocation) {
                callCount++;
                if (callCount <= failCount) {
                    throw new RuntimeException("SMTP down");
                }
                return null;
            }
        }).when(mailSender).send(any(MimeMessage.class));

    }

    //  ─────────── tests ───────────

    @Test
    void shouldOpenCircuitBreaker_whenFailureThresholdExceeded() {
        openCircuitBreaker();
        verify(mailSender, times(SLIDING_WINDOW_SIZE)).send(any(MimeMessage.class));

        clearInvocations(mailSender);

        assertThatThrownBy(() -> emailProvider.send(event, "<h1>Hello!</h1>"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Email service unavailable");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldOpenAtExactThreshold_whenFailureRateEquals50Percent() {
        // 5/10 = exactly 50% threshold
        stubMailSenderWithFailures(SLIDING_WINDOW_SIZE / 2);

        for (int i = 0; i < SLIDING_WINDOW_SIZE; i++) {
            try {
                emailProvider.send(event, "<h1>Hello!</h1>");
            } catch (RuntimeException ignored) {
            }
        }

        clearInvocations(mailSender);

        assertThatThrownBy(() -> emailProvider.send(event, "<h1>Hi</h1>"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Email service unavailable");

        verify(mailSender, never()).send(any(MimeMessage.class));

    }

    @Test
    void shouldKeepCircuitClosed_whenFailureRateBelowThreshold() {
        // 4/10 = 40% < 50% threshold
        stubMailSenderWithFailures(FAILURE_RATE_THRESHOLD_PCT * SLIDING_WINDOW_SIZE / 100 - 1);

        for (int i = 0; i < SLIDING_WINDOW_SIZE; i++) {
            try {
                emailProvider.send(event, "<h1>Hello!</h1>");
            } catch (RuntimeException ignored) {
            }
        }

        clearInvocations(mailSender);

        emailProvider.send(event, "<h1>Hello</h1>");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldAllowCallsThrough_whenCircuitBreakerInHalfOpenState() {
        openCircuitBreaker();
        transitionToHalfOpen();

        clearInvocations(mailSender);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailProvider.send(event, "<h1>Hello!</h1>");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldCloseCircuit_whenHalfOpenCallsSucceed() {
        openCircuitBreaker();
        transitionToHalfOpen();

        doNothing().when(mailSender).send(any(MimeMessage.class));

        makeSuccessfulCalls(PERMITTED_CALLS_IN_HALF_OPEN);
        clearInvocations(mailSender);

        emailProvider.send(event, "<h1>Hello!</h1>");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void shouldReopenCircuit_whenHalfOpenCallFails() {
        openCircuitBreaker();
        transitionToHalfOpen();

        doThrow(new RuntimeException("SMTP down"))
            .when(mailSender).send(any(MimeMessage.class));

        for (int i = 0; i < PERMITTED_CALLS_IN_HALF_OPEN; i++) {
            try {
                emailProvider.send(event, "<h1>Hello!</h1>");
            } catch (RuntimeException ignored) {
            }
        }

        clearInvocations(mailSender);

        assertThatThrownBy(() -> emailProvider.send(event, "<h1>Hello!</h1>"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Email service unavailable");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void shouldNotCountOldFailures_whenSlidingWindowMoves() {
        // 4F + 6S = 40% < 50% → window is full, CB still closed
        stubMailSenderWithFailures(FAILURE_RATE_THRESHOLD_PCT * SLIDING_WINDOW_SIZE / 100 - 1);
        for (int i = 0; i < SLIDING_WINDOW_SIZE; i++) {
            try {
                emailProvider.send(event, "<h1>Hello!</h1>");
            } catch (RuntimeException ignored) {
            }
        }

        // 10 success → old failures pushed out, rate = 0%
        makeSuccessfulCalls(SLIDING_WINDOW_SIZE);

        // 4 new failures → 40% < 50% → CB still closed
        makeFailingCalls(FAILURE_RATE_THRESHOLD_PCT * SLIDING_WINDOW_SIZE / 100 - 1);

        clearInvocations(mailSender);
        doNothing().when(mailSender).send(any(MimeMessage.class));

        emailProvider.send(event, "<h1>Hello!</h1>");
        verify(mailSender).send(any(MimeMessage.class));
    }
}
