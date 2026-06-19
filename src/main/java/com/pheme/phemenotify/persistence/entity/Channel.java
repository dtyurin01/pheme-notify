package com.pheme.phemenotify.persistence.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.pheme.phemenotify.utils.EnumUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Channel {
  EMAIL("EMAIL", "html"),
  SMS("SMS", "txt"),
  PUSH("PUSH", "txt");

  private final String value;
  @Getter private final String templateExtension;

  @JsonCreator
  public static Channel fromValue(String value) {
    return EnumUtils.fromValue(Channel.class, value);
  }

  @Override
  @JsonValue
  public String toString() {
    return value;
  }
}
