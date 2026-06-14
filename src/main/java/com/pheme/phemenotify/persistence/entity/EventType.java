package com.pheme.phemenotify.persistence.entity;

import com.fasterxml.jackson.annotation.JsonValue;

// Acts as a bridge between the database (VARCHAR) and Java application logic.
public interface EventType {

  /**
   * Returns a unique string identifier for the event (e.g., "ORDER_COMPLETED"). Used as: 1. The
   * value stored in the 'event_type' column in PostgreSQL. 2. The lookup key for the
   * EventTypeRegistry. 3. The value in JSON payloads (Kafka/API).
   */
  @JsonValue
  String getCode();

  default String getDescription() {
    return getCode();
  }
}
