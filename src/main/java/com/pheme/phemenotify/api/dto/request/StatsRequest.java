package com.pheme.phemenotify.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record StatsRequest (
    @NotNull
    LocalDate startDate,
    @NotNull
    LocalDate endDate
){}
