package com.pheme.phemenotify.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "pheme.resilience.email")
public class ResilienceProperties {

    /** Number of calls in the sliding window used to calculate the failure rate. */
    private int slidingWindowSize = 10;

    /** Failure rate threshold (%) above which the circuit breaker opens. */
    private float failureRateThreshold = 50;

    /** How long the circuit breaker stays OPEN before transitioning to HALF_OPEN. */
    private Duration waitDurationInOpenState = Duration.ofSeconds(30);

    /** Number of calls allowed in HALF_OPEN state to decide whether to close the circuit. */
    private int permittedCallsInHalfOpen = 3;
}