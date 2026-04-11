package com.wwa.deploymentagent.agents.testing.domain;

public enum TestingStage {
    UAT;

    public static TestingStage fromString(String value) {
        return valueOf(value);
    }
}
