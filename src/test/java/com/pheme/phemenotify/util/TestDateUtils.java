package com.pheme.phemenotify.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public final class TestDateUtils {
    private TestDateUtils(){}

    private static final ZoneOffset UTC = ZoneOffset.UTC;

    public static Instant daysAgo(int days){
        return LocalDate.now(UTC).minusDays(days).atStartOfDay(UTC).toInstant();
    }

    public static LocalDate localDaysAgo(int days) {
        return LocalDate.now(UTC).minusDays(days);
    }

    public static Instant startOfDay(LocalDate date){
        return date.atStartOfDay(UTC).toInstant();
    }
    public static Instant endOfDay(LocalDate date){
        return date.plusDays(1).atStartOfDay(UTC).toInstant();
    }
    public static Instant today(){
        return daysAgo(0);
    }
}
