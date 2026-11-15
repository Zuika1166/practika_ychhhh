package ru.practice.helpdesk;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrorHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);
  private final Clock clock;

  public ApiErrorHandler(Clock clock) {
    this.clock = clock;
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status,
      String code,
      String message,
      HttpServletRequest request,
      Map<String, String> fields) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                clock.instant(), status.value(), code, message, request.getRequestURI(), fields));
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ErrorResponse> business(ApiException error, HttpServletRequest request) {
    return response(error.getStatus(), error.getCode(), error.getMessage(), request, Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> validation(
      MethodArgumentNotValidException error, HttpServletRequest request) {
    Map<String, String> fields = new LinkedHashMap<>();
    error
        .getBindingResult()
        .getFieldErrors()
        .forEach(field -> fields.put(field.getField(), field.getDefaultMessage()));
    return response(
        HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Некорректные данные", request, fields);
  }

  @ExceptionHandler({
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ErrorResponse> malformed(Exception error, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Проверьте поля и параметры запроса",
        request,
        Map.of());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> period(IllegalArgumentException error, HttpServletRequest request) {
    return response(
        HttpStatus.BAD_REQUEST,
        "INVALID_PERIOD",
        "Начало периода должно быть раньше конца",
        request,
        Map.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> missing(Exception error, HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ресурс не найден", request, Map.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> method(Exception error, HttpServletRequest request) {
    return response(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "Метод не поддерживается",
        request,
        Map.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> unexpected(Exception error, HttpServletRequest request) {
    LOG.error("Request failed: {}", request.getRequestURI(), error);
    return response(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "Внутренняя ошибка сервиса",
        request,
        Map.of());
  }
}
