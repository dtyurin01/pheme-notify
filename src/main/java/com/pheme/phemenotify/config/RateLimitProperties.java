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
  @Valid private ChannelLimit email = new ChannelLimit(5, Duration.ofHours(1));

  /** SMS limit: 3 requests per hour. Property: notification.rate-limit.sms */
  @Valid private ChannelLimit sms = new ChannelLimit(3, Duration.ofHours(1));

  /** Push limit: 20 requests per hour. Property: notification.rate-limit.push */
  @Valid private ChannelLimit push = new ChannelLimit(20, Duration.ofHours(1));

  // Needed for setter-based binding of top-level fields
  public void setEmail(ChannelLimit email) {
    this.email = email;
  }

  public void setSms(ChannelLimit sms) {
    this.sms = sms;
  }

  public void setPush(ChannelLimit push) {
    this.push = push;
  }

  public record ChannelLimit(@Min(1) int maxRequests, @NotNull Duration window) {}
}
