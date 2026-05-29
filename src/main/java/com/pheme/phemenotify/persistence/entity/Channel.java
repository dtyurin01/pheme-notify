package com.pheme.phemenotify.persistence.entity;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.pheme.phemenotify.utils.EnumUtils;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum Channel {
    EMAIL("EMAIL"),
    SMS("SMS"),
    PUSH("PUSH");

    private final String value;

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
