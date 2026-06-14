package com.pheme.phemenotify.api.exception;

import java.net.URI;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail resourceNotFoundException(ResourceNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setType(URI.create("https://pheme.com/errors/not-found"));
    problem.setTitle("Resource Not Found");
    return problem;
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ProblemDetail handleRateLimit(RateLimitExceededException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    problem.setType(URI.create("https://pheme.com/errors/rate-limit-exceeded"));
    problem.setTitle("Rate Limit Exceeded");
    return problem;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String detail =
        exception.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .findFirst()
            .orElse("Validation Failed");
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setType(URI.create("https://pheme.com/errors/validation-failed"));
    problem.setTitle("Validation Failed");
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(InvalidDateRangeException.class)
  public ProblemDetail handleInvalidDateRange(InvalidDateRangeException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(URI.create("https://pheme.com/errors/invalid-date-range"));
    problem.setTitle("Invalid Date Range");
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail exception(Exception exception) {
    String errorId = UUID.randomUUID().toString();
    log.error("Unexpected error [id={}]", errorId, exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problem.setType(URI.create("https://pheme.com/errors/internal-error"));
    problem.setTitle("Internal Server Error");
    return problem;
  }
}
