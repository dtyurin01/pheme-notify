package com.pheme.phemenotify.util;

import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.ConfigBuilder;

public class NoOpCircuitBreakerFactory
    extends CircuitBreakerFactory<Object, ConfigBuilder<Object>> {

  private static final CircuitBreaker INSTANCE =
      new CircuitBreaker() {
        @Override
        public <T> T run(Supplier<T> toRun, Function<@Nullable Throwable, T> fallback) {
          try {
            return toRun.get();
          } catch (Exception e) {
            return fallback.apply(e);
          }
        }
      };

  @Override
  public CircuitBreaker create(String id) {
    return INSTANCE;
  }

  @Override
  protected ConfigBuilder<Object> configBuilder(String id) {
    return Object::new;
  }

  @Override
  public void configureDefault(Function<String, Object> defaultConfiguration) {
    // No-op
  }
}
