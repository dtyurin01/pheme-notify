package com.pheme.phemenotify.api.exception;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final URI TYPE_NOT_FOUND = URI.create("https://pheme.com/errors/not-found");
  private static final URI TYPE_RATE_LIMIT_EXCEEDED =
      URI.create("https://pheme.com/errors/rate-limit-exceeded");
  private static final URI TYPE_VALIDATION_FAILED =
      URI.create("https://pheme.com/errors/validation-failed");
  private static final URI TYPE_INVALID_DATE_RANGE =
      URI.create("https://pheme.com/errors/invalid-date-range");
  private static final URI TYPE_INTERNAL_ERROR =
      URI.create("https://pheme.com/errors/internal-error");

  private static final String TITLE_NOT_FOUND = "Resource Not Found";
  private static final String TITLE_RATE_LIMIT_EXCEEDED = "Rate Limit Exceeded";
  private static final String TITLE_VALIDATION_FAILED = "Validation Failed";
  private static final String TITLE_INVALID_DATE_RANGE = "Invalid Date Range";
  private static final String TITLE_INTERNAL_ERROR = "Internal Server Error";

  @ExceptionHandler(ResourceNotFoundException.class)
  public ProblemDetail handleResourceNotFound(ResourceNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setType(TYPE_NOT_FOUND);
    problem.setTitle(TITLE_NOT_FOUND);
    return problem;
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ProblemDetail handleRateLimit(RateLimitExceededException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
    problem.setType(TYPE_RATE_LIMIT_EXCEEDED);
    problem.setTitle(TITLE_RATE_LIMIT_EXCEEDED);
    return problem;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, errors.getFirst());
    problem.setType(TYPE_VALIDATION_FAILED);
    problem.setTitle(TITLE_VALIDATION_FAILED);
    problem.setProperty("errors", errors);
    return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  @Override
  protected ResponseEntity<Object> handleMissingServletRequestParameter(
      MissingServletRequestParameterException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(TYPE_VALIDATION_FAILED);
    problem.setTitle(TITLE_VALIDATION_FAILED);
    return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
    String detail = "%s: invalid value '%s'".formatted(exception.getName(), exception.getValue());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    problem.setType(TYPE_VALIDATION_FAILED);
    problem.setTitle(TITLE_VALIDATION_FAILED);
    return problem;
  }

  @ExceptionHandler(InvalidDateRangeException.class)
  public ProblemDetail handleInvalidDateRange(InvalidDateRangeException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setType(TYPE_INVALID_DATE_RANGE);
    problem.setTitle(TITLE_INVALID_DATE_RANGE);
    return problem;
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpectedException(Exception exception) {
    String errorId = UUID.randomUUID().toString();
    log.error("Unexpected error [id={}]", errorId, exception);
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    problem.setType(TYPE_INTERNAL_ERROR);
    problem.setTitle(TITLE_INTERNAL_ERROR);
    problem.setProperty("errorId", errorId);
    return problem;
  }
}
