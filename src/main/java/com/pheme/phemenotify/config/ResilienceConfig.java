package com.pheme.phemenotify.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class ResilienceConfig {

  private final ResilienceProperties props;

  @Bean
  public Customizer<Resilience4JCircuitBreakerFactory> emailCircuitBreakerConfig() {
    CircuitBreakerConfig config =
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(props.getSlidingWindowSize())
            .failureRateThreshold(props.getFailureRateThreshold())
            .waitDurationInOpenState(props.getWaitDurationInOpenState())
            .permittedNumberOfCallsInHalfOpenState(props.getPermittedCallsInHalfOpen())
            .build();

    return factory -> factory.configure(builder -> builder.circuitBreakerConfig(config), "email");
  }

  @Bean
  public TaggedCircuitBreakerMetrics circuitBreakerMetrics(
      Resilience4JCircuitBreakerFactory factory, MeterRegistry meterRegistry) {
    TaggedCircuitBreakerMetrics metrics =
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(factory.getCircuitBreakerRegistry());
    metrics.bindTo(meterRegistry);
    return metrics;
  }
}
