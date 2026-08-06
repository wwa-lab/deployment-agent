package com.wwa.agenthub.web.exception;

import com.wwa.agenthub.contracts.dto.ErrorResponseDto;
import com.wwa.agenthub.errors.AppException;
import com.wwa.agenthub.errors.ImportValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Global exception handler – maps domain exceptions to HTTP responses.
 * Preserves the error code / message / details structure from the TypeScript AppError contract.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponseDto> handleAppException(AppException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponseDto(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponseDto> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDto("OPTIMISTIC_LOCK_CONFLICT",
                        "Concurrent update conflict. Reload and retry.", null));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponseDto("VALIDATION_ERROR", ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponseDto("VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<ErrorResponseDto> handleImportValidation(ImportValidationException ex) {
        String details = ex.getErrors().stream()
                .map(e -> "Row " + e.row() + " [" + e.column() + "]: " + e.message())
                .collect(Collectors.joining("; "));
        return ResponseEntity
                .unprocessableEntity()
                .body(new ErrorResponseDto("IMPORT_VALIDATION_ERROR", ex.getMessage(), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .internalServerError()
                .body(new ErrorResponseDto("INTERNAL_ERROR", "An unexpected error occurred", null));
    }
}
