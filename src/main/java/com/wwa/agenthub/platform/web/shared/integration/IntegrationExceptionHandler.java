package com.wwa.agenthub.platform.web.shared.integration;

import com.wwa.agenthub.contracts.dto.integration.IntegrationEnvelope;
import com.wwa.agenthub.errors.AppException;
import com.wwa.agenthub.platform.domain.integration.IntegrationApiException;
import com.wwa.agenthub.platform.web.common.CorrelationIdFilter;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.List;

@RestControllerAdvice(basePackages = "com.wwa.agenthub.platform.web.shared.integration")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class IntegrationExceptionHandler {

    @ExceptionHandler(IntegrationApiException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleIntegration(IntegrationApiException exception) {
        return response(
                exception.getStatus(),
                exception.getCode(),
                exception.getMessage(),
                exception.isRetryable(),
                List.of());
    }

    @ExceptionHandler(AppException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleLegacyDomain(AppException exception) {
        return response(
                HttpStatus.valueOf(exception.getStatusCode()),
                exception.getCode(),
                exception.getMessage(),
                false,
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        List<IntegrationEnvelope.Detail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new IntegrationEnvelope.Detail(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                "Request validation failed.",
                false,
                details);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleBind(BindException exception) {
        List<IntegrationEnvelope.Detail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new IntegrationEnvelope.Detail(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                "Request validation failed.",
                false,
                details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleConstraint(
            ConstraintViolationException exception
    ) {
        List<IntegrationEnvelope.Detail> details = exception.getConstraintViolations().stream()
                .map(violation -> new IntegrationEnvelope.Detail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()))
                .toList();
        return response(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED",
                "Request validation failed.",
                false,
                details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleInvalidJson(
            HttpMessageNotReadableException exception
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_JSON",
                "The JSON request body is malformed.",
                false,
                List.of());
    }

    @ExceptionHandler({MissingRequestHeaderException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleMissingRequestValue(Exception exception) {
        String code = exception instanceof MissingRequestHeaderException missing
                && "Idempotency-Key".equalsIgnoreCase(missing.getHeaderName())
                ? "INVALID_IDEMPOTENCY_KEY"
                : "INVALID_REQUEST";
        return response(
                HttpStatus.BAD_REQUEST,
                code,
                "The request is missing a required value.",
                false,
                List.of());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleMissingPart(
            MissingServletRequestPartException exception
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The multipart request is missing a required part.",
                false,
                List.of(new IntegrationEnvelope.Detail(exception.getRequestPartName(), "is required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "A request parameter has an invalid value.",
                false,
                List.of(new IntegrationEnvelope.Detail(exception.getName(), "has an invalid value")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleUploadTooLarge(
            MaxUploadSizeExceededException exception
    ) {
        return response(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "ARTIFACT_TOO_LARGE",
                "The Artifact exceeds the configured size limit.",
                false,
                List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception
    ) {
        return response(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "UNSUPPORTED_MEDIA_TYPE",
                "The request media type is not supported.",
                false,
                List.of());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleInvalidMultipart(
            MultipartException exception
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "The multipart request is malformed.",
                false,
                List.of());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleConcurrency(Exception exception) {
        log.warn("Concurrent Atlas Integration update requestId={}",
                CorrelationIdFilter.current(), exception);
        return response(
                HttpStatus.CONFLICT,
                "STALE_EXECUTION",
                "The resource changed concurrently. Refresh and retry.",
                true,
                List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<IntegrationEnvelope.ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected Atlas Integration failure requestId={}", CorrelationIdFilter.current(), exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred.",
                false,
                List.of());
    }

    private static ResponseEntity<IntegrationEnvelope.ErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            boolean retryable,
            List<IntegrationEnvelope.Detail> details
    ) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            builder.header("Retry-After", "1");
        }
        return builder.body(IntegrationEnvelope.ErrorResponse.of(
                code, message, retryable, CorrelationIdFilter.current(), details));
    }
}
