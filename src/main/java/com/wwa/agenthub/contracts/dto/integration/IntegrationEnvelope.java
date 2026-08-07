package com.wwa.agenthub.contracts.dto.integration;

import java.util.List;

public final class IntegrationEnvelope {

    private IntegrationEnvelope() {
    }

    public record Success<T>(boolean success, T data) {
        public static <T> Success<T> of(T data) {
            return new Success<>(true, data);
        }
    }

    public record Page<T>(boolean success, List<T> data, Cursor meta) {
        public Page {
            data = data == null ? List.of() : List.copyOf(data);
        }

        public static <T> Page<T> of(List<T> data, String nextCursor, boolean hasMore) {
            return new Page<>(true, data, new Cursor(nextCursor, hasMore));
        }
    }

    public record Cursor(String nextCursor, boolean hasMore) {
    }

    public record ErrorResponse(boolean success, Error error) {
        public static ErrorResponse of(
                String code,
                String message,
                boolean retryable,
                String requestId,
                List<Detail> details
        ) {
            return new ErrorResponse(false, new Error(
                    code,
                    message,
                    retryable,
                    requestId,
                    details == null ? List.of() : List.copyOf(details)));
        }
    }

    public record Error(
            String code,
            String message,
            boolean retryable,
            String requestId,
            List<Detail> details
    ) {
    }

    public record Detail(String field, String message) {
    }
}
