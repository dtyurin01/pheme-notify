package com.pheme.phemenotify.config.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * Validates that a {@link java.time.Duration} is strictly positive (> 0).
 * {@code Duration.ZERO} and negative values are rejected.
 * {@code null} is considered valid — combine with {@code @NotNull} if needed.
 */
@Documented
@Constraint(validatedBy = PositiveDurationValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PositiveDuration {
    String message() default "must be a positive duration";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
