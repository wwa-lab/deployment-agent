package com.wwa.agenthub.contracts.dto;

/** Error response body returned by GlobalExceptionHandler. */
public record ErrorResponseDto(String code, String message, Object details) {}
