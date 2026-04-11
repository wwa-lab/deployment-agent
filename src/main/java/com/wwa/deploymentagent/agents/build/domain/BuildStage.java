package com.wwa.deploymentagent.agents.build.domain;

public enum BuildStage {
    DEV;

    public static BuildStage fromString(String value) {
        return valueOf(value);
    }
}
