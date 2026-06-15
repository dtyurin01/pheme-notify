package com.pheme.phemenotify.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configures API versioning via the {@code X-API-Version} request header. Controllers declare their
 * version with {@code @GetMapping(version = "1")} etc. Requests without the header use the default
 * version below.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final String DEFAULT_VERSION = "1";

  @Override
  public void configureApiVersioning(ApiVersionConfigurer configurer) {
    configurer
        .useRequestHeader("X-API-Version")
        .addSupportedVersions(DEFAULT_VERSION)
        .setDefaultVersion(DEFAULT_VERSION);
  }
}
