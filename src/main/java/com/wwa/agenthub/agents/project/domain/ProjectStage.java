package com.wwa.agenthub.agents.project.domain;

public enum ProjectStage {
    REQUIREMENT,
    FUNCTIONAL_DESIGN,
    TECHNICAL_DESIGN,
    DEVELOPMENT,
    TESTING,
    PERFORMANCE_TEST,
    RESULT_SIGNOFF,
    BUSINESS_ENDORSEMENT,
    CAB,
    DEPLOYMENT,
    POST_IMPLEMENTATION;

    public static ProjectStage fromString(String value) {
        return valueOf(value);
    }
}
