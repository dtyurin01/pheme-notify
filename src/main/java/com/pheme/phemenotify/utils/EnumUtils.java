package com.pheme.phemenotify.utils;

import java.util.Arrays;

public final class EnumUtils {
    private EnumUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static <T extends Enum<T>> T fromValue(Class<T> enumClass, String value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Unknown %s: %s", enumClass.getSimpleName(), value)
                ));
    }
}
