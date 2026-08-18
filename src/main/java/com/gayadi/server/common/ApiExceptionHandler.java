package com.gayadi.server.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return error(ex.getStatus(), ex.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        return bindingError(ex.getBindingResult(), request.getRequestURI());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBinding(BindException ex, HttpServletRequest request) {
        return bindingError(ex.getBindingResult(), request.getRequestURI());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        String message = ex.getAllErrors().stream()
                .map(this::validationMessage)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("요청값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, message, request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintValidation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .map(this::validationMessage)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("요청값이 올바르지 않습니다.");
        return error(HttpStatus.BAD_REQUEST, message, request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String name = ex.getName();
        String message = "'" + name + "' 값의 형식이 올바르지 않습니다.";
        return error(HttpStatus.BAD_REQUEST, message, request.getRequestURI(), Map.of("parameter", name));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String name = ex.getParameterName();
        String message = "'" + name + "' 요청값이 필요합니다.";
        return error(HttpStatus.BAD_REQUEST, message, request.getRequestURI(), Map.of("parameter", name));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "요청 본문 형식이 올바르지 않습니다.",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "요청 본문은 application/json 형식으로 보내 주세요.",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("데이터 제약 조건 충돌: {} {}", request.getMethod(), request.getRequestURI());
        return error(
                HttpStatus.CONFLICT,
                "이미 사용 중인 값이거나 다른 데이터와 연결되어 있어 처리할 수 없습니다.",
                request.getRequestURI(),
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("처리하지 못한 요청 오류: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다.",
                request.getRequestURI(),
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> bindingError(BindingResult result, String path) {
        String message = result.getFieldErrors().stream()
                .findFirst()
                .map(this::fieldMessage)
                .orElse("요청값이 올바르지 않습니다.");

        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError fieldError : result.getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), defaultMessage(fieldError));
        }
        return error(HttpStatus.BAD_REQUEST, message, path, details);
    }

    private String fieldMessage(FieldError error) {
        return error.getField() + ": " + defaultMessage(error);
    }

    private String defaultMessage(FieldError error) {
        return validationMessage(error.getDefaultMessage(), error.getCode());
    }

    private String validationMessage(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        String code = codes == null || codes.length == 0 ? null : codes[codes.length - 1];
        return validationMessage(error.getDefaultMessage(), code);
    }

    private String validationMessage(ConstraintViolation<?> violation) {
        String code = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
        return validationMessage(violation.getMessage(), code);
    }

    private String validationMessage(String message, String code) {
        if (message != null && message.matches(".*[가-힣].*")) {
            return message;
        }
        return switch (code == null ? "" : code) {
            case "NotNull" -> "값이 필요합니다.";
            case "NotBlank" -> "빈 값일 수 없습니다.";
            case "NotEmpty" -> "한 개 이상 입력해야 합니다.";
            case "Size" -> "길이 또는 개수가 허용 범위를 벗어났습니다.";
            case "Email" -> "이메일 형식이 올바르지 않습니다.";
            case "Pattern" -> "허용된 형식과 일치하지 않습니다.";
            case "Positive" -> "0보다 큰 값이어야 합니다.";
            case "PositiveOrZero" -> "0 이상의 값이어야 합니다.";
            case "Negative" -> "0보다 작은 값이어야 합니다.";
            case "NegativeOrZero" -> "0 이하의 값이어야 합니다.";
            case "Min", "DecimalMin" -> "허용된 최솟값보다 작습니다.";
            case "Max", "DecimalMax" -> "허용된 최댓값보다 큽니다.";
            case "Future", "FutureOrPresent" -> "현재 이후의 값이어야 합니다.";
            case "Past", "PastOrPresent" -> "현재 이전의 값이어야 합니다.";
            default -> "값이 올바르지 않습니다.";
        };
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> details) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.name(),
                message,
                path,
                UUID.randomUUID().toString(),
                Map.copyOf(details)
        );
        return ResponseEntity.status(status).body(body);
    }
}
