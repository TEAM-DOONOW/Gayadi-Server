package com.gayadi.server.common.exception;

import com.gayadi.server.common.response.ApiErrorDetail;
import com.gayadi.server.common.response.ApiErrorResponse;
import com.gayadi.server.common.response.ApiErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ApiErrorResponseFactory responseFactory;

    public GlobalExceptionHandler(ApiErrorResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        String message = formatDefaultMessage(errorCode.defaultMessage(), ex.getMessageArguments());
        if (errorCode.status().is5xxServerError()) {
            String traceId = responseFactory.newTraceId();
            log.error("외부 연동 또는 서비스 처리 오류: {} {} code={} traceId={}",
                    request.getMethod(), request.getRequestURI(), errorCode.code(), traceId, ex);
            return error(errorCode, message, request.getRequestURI(), null, traceId);
        }
        return error(errorCode, message, request.getRequestURI(), null);
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
        List<ApiErrorDetail> details = ex.getAllErrors().stream()
                .map(error -> new ApiErrorDetail(null, validationMessage(error)))
                .toList();
        return error(CommonErrorCode.INVALID_REQUEST, request.getRequestURI(), details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintValidation(
            ConstraintViolationException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getConstraintViolations().stream()
                .map(violation -> new ApiErrorDetail(
                        violation.getPropertyPath().toString(), validationMessage(violation)))
                .toList();
        return error(CommonErrorCode.INVALID_REQUEST, request.getRequestURI(), details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String name = ex.getName();
        String message = "'" + name + "' 값의 형식이 올바르지 않습니다.";
        return error(CommonErrorCode.INVALID_PARAMETER_TYPE, message, request.getRequestURI(),
                List.of(new ApiErrorDetail(name, message)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String name = ex.getParameterName();
        String message = "'" + name + "' 요청값이 필요합니다.";
        return error(CommonErrorCode.MISSING_REQUIRED_PARAMETER, message, request.getRequestURI(),
                List.of(new ApiErrorDetail(name, message)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return error(CommonErrorCode.MALFORMED_REQUEST_BODY, request.getRequestURI(), null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return error(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request.getRequestURI(), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        return error(CommonErrorCode.RESOURCE_NOT_FOUND, request.getRequestURI(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String method = ex.getMethod();
        String message = "'" + method + "' 메서드는 이 경로에서 지원하지 않습니다.";
        ApiErrorDetail detail = new ApiErrorDetail("method", message);
        String traceId = responseFactory.newTraceId();
        ApiErrorResponse body = responseFactory.create(
                CommonErrorCode.METHOD_NOT_ALLOWED, message,
                request.getRequestURI(), traceId, List.of(detail));

        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            response.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(body);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException ex, HttpServletRequest request) {
        return error(CommonErrorCode.NOT_ACCEPTABLE, request.getRequestURI(), null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        String name = ex.getRequestPartName();
        String message = "'" + name + "' 요청 항목이 필요합니다.";
        return error(CommonErrorCode.MISSING_REQUIRED_PARAMETER, message, request.getRequestURI(),
                List.of(new ApiErrorDetail(name, message)));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRequestTooLarge(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return error(CommonErrorCode.REQUEST_TOO_LARGE, request.getRequestURI(), null);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleAsyncRequestTimeout(
            AsyncRequestTimeoutException ex, HttpServletRequest request) {
        return error(CommonErrorCode.ASYNC_REQUEST_TIMEOUT, request.getRequestURI(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataConflict(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String traceId = responseFactory.newTraceId();
        log.warn("데이터 제약 조건 충돌: {} {} traceId={}",
                request.getMethod(), request.getRequestURI(), traceId);
        return error(CommonErrorCode.DATA_CONFLICT, request.getRequestURI(), null, traceId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = responseFactory.newTraceId();
        log.error("처리하지 못한 요청 오류: {} {} traceId={}",
                request.getMethod(), request.getRequestURI(), traceId, ex);
        return error(CommonErrorCode.INTERNAL_SERVER_ERROR, request.getRequestURI(), null, traceId);
    }

    private ResponseEntity<ApiErrorResponse> bindingError(BindingResult result, String path) {
        List<ApiErrorDetail> details = new ArrayList<>();
        for (FieldError fieldError : result.getFieldErrors()) {
            ApiErrorDetail detail = new ApiErrorDetail(fieldError.getField(), defaultMessage(fieldError));
            if (!details.contains(detail)) {
                details.add(detail);
            }
        }
        result.getGlobalErrors().forEach(error -> {
            ApiErrorDetail detail = new ApiErrorDetail(null, validationMessage(error));
            if (!details.contains(detail)) {
                details.add(detail);
            }
        });
        return error(CommonErrorCode.INVALID_REQUEST, path, details);
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

    private String formatDefaultMessage(String message, Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return message;
        }
        return MessageFormat.format(message, arguments);
    }

    private ResponseEntity<ApiErrorResponse> error(
            ErrorCode errorCode,
            String path,
            List<ApiErrorDetail> details) {
        return error(errorCode, errorCode.defaultMessage(), path, details);
    }

    private ResponseEntity<ApiErrorResponse> error(
            ErrorCode errorCode,
            String message,
            String path,
            List<ApiErrorDetail> details) {
        return error(errorCode, message, path, details, responseFactory.newTraceId());
    }

    private ResponseEntity<ApiErrorResponse> error(
            ErrorCode errorCode,
            String path,
            List<ApiErrorDetail> details,
            String traceId) {
        return error(errorCode, errorCode.defaultMessage(), path, details, traceId);
    }

    private ResponseEntity<ApiErrorResponse> error(
            ErrorCode errorCode,
            String message,
            String path,
            List<ApiErrorDetail> details,
            String traceId) {
        ApiErrorResponse body = responseFactory.create(
                errorCode, message, path, traceId, details);
        return ResponseEntity.status(errorCode.status()).body(body);
    }

}
