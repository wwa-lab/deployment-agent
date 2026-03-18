package com.wwa.deploymentagent.contracts.dto;

import java.util.List;

/** Generic paginated response wrapper. */
public record PaginatedResponseDto<T>(
        List<T> data,
        long total,
        int page,
        int size
) {}
