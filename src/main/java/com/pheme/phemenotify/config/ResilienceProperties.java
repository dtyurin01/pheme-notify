package com.pheme.phemenotify.config;

import com.pheme.phemenotify.config.validation.PositiveDuration;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Circuit Breaker configuration for notification providers.
 * Bound from {@code pheme.resilience.email.*} in application.yaml.
 */
@Getter
@Validated
@ConfigurationProperties(prefix = "pheme.resilience.email")
public class ResilienceProperties {

    /** Number of calls in the sliding window used to calculate the failure rate. */
    @Min(1)
    private final int slidingWindowSize;

    /** Failure rate threshold (%) above which the circuit breaker opens. Must be in (0, 100]. */
    @Positive
    @DecimalMax("100.0")
    private final float failureRateThreshold;

    /** How long the circuit breaker stays OPEN before transitioning to HALF_OPEN. */
    @NotNull
    @PositiveDuration
    private final Duration waitDurationInOpenState;

    /** Number of calls allowed in HALF_OPEN state to decide whether to close the circuit. */
    @Min(1)
    private final int permittedCallsInHalfOpen;

    public ResilienceProperties(int slidingWindowSize, float failureRateThreshold,
                                Duration waitDurationInOpenState, int permittedCallsInHalfOpen) {
        this.slidingWindowSize = slidingWindowSize;
        this.failureRateThreshold = failureRateThreshold;
        this.waitDurationInOpenState = waitDurationInOpenState;
        this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
    }
}
