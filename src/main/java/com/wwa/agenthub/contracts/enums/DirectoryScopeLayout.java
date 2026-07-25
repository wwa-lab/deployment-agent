package com.wwa.agenthub.contracts.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Layout mode for a directory scope section. */
public enum DirectoryScopeLayout {
    STAGE_STRIP("stage-strip"),
    BUCKETS("buckets");

    private final String jsonValue;

    DirectoryScopeLayout(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }

    @JsonCreator
    public static DirectoryScopeLayout fromJson(String value) {
        if (value == null) {
            return null;
        }
        for (DirectoryScopeLayout layout : values()) {
            if (layout.jsonValue.equals(value)) {
                return layout;
            }
        }
        throw new IllegalArgumentException("Unknown directory scope layout: " + value);
    }
}
