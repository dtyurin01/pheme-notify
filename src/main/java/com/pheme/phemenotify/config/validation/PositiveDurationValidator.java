package com.pheme.phemenotify.config.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Duration;

public class PositiveDurationValidator implements ConstraintValidator<PositiveDuration, Duration> {
  @Override
  public boolean isValid(Duration value, ConstraintValidatorContext context) {
    if (value == null) return true;

    return !value.isNegative() && !value.isZero();
  }
}
