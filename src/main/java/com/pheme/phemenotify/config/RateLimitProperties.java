package com.pheme.phemenotify.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
@ConfigurationProperties(prefix = "notification.rate-limit")
public class RateLimitProperties {

  /** Email limit: 5 requests per hour. Property: notification.rate-limit.email */
  @Valid private final ChannelLimit email;

  /** SMS limit: 3 requests per hour. Property: notification.rate-limit.sms */
  @Valid private final ChannelLimit sms;

  /** Push limit: 20 requests per hour. Property: notification.rate-limit.push */
  @Valid private final ChannelLimit push;

  public RateLimitProperties(
      @Valid ChannelLimit email, @Valid ChannelLimit sms, @Valid ChannelLimit push) {
    this.email = email != null ? email : new ChannelLimit(5, Duration.ofHours(1));
    this.sms = sms != null ? sms : new ChannelLimit(3, Duration.ofHours(1));
    this.push = push != null ? push : new ChannelLimit(20, Duration.ofHours(1));
  }

  public record ChannelLimit(@Min(1) int maxRequests, @NotNull Duration window) {}
}
