package com.wwa.agenthub.agents.build.domain;

public enum BuildStage {
    DEV;

    public static BuildStage fromString(String value) {
        return valueOf(value);
    }
}
