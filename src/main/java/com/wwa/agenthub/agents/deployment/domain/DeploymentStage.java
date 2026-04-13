package com.wwa.agenthub.agents.deployment.domain;

public enum DeploymentStage {
    SIT,
    UAT,
    PROD;

    public static DeploymentStage fromString(String value) {
        return valueOf(value);
    }
}
