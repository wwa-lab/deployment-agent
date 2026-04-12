package com.wwa.deploymentagent.agents.deployment.domain;

public enum DeploymentStage {
    SIT,
    UAT,
    PROD;

    public static DeploymentStage fromString(String value) {
        return valueOf(value);
    }
}
