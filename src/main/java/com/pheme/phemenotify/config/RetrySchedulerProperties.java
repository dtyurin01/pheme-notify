package com.pheme.phemenotify.config;

import com.pheme.phemenotify.config.validation.PositiveDuration;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration for the failed-notification retry scheduler.
 * Bound from {@code notification.retry-scheduler.*} in application.yaml.
 *
 * <p>Backoff formula: {@code backoffBase * 2^(retryCount - 1)}.
 * Example with defaults: retry 1 → 5m, retry 2 → 10m, retry 3 → 20m.
 *
 * <p>{@code maxAttempts} counts only retry attempts, not the original delivery.
 */
@Getter
@Validated
@ConfigurationProperties(prefix = "notification.retry-scheduler")
public class RetrySchedulerProperties {

    /**
     * Maximum number of retry attempts before marking a notification as FAILED.
     * Does not include the original delivery attempt.
     * Property: {@code notification.retry-scheduler.max-attempts}
     */
    @Min(1)
    private final int maxAttempts;

    /**
     * Base duration for exponential backoff between retries.
     * Actual delay = {@code backoffBase * 2^(retryCount - 1)}.
     * Property: {@code notification.retry-scheduler.backoff-base}
     */
    @NotNull
    @PositiveDuration
    private final Duration backoffBase;

    /**
     * How often the scheduler polls for failed notifications ready to retry.
     * Property: {@code notification.retry-scheduler.fixed-delay}
     */
    @NotNull
    @PositiveDuration
    private final Duration fixedDelay;

    public RetrySchedulerProperties(int maxAttempts, Duration backoffBase, Duration fixedDelay) {
        this.maxAttempts = maxAttempts;
        this.backoffBase = backoffBase;
        this.fixedDelay = fixedDelay;
    }
}
