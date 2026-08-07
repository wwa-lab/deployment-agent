package com.wwa.agenthub.contracts.dto.integration;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * Rejects undeclared fields only on the Atlas Integration command surface.
 * Existing platform APIs intentionally retain their established Jackson
 * compatibility behavior.
 */
public interface StrictIntegrationRequest {

    @JsonAnySetter
    default void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown Atlas Integration field: " + fieldName);
    }
}
