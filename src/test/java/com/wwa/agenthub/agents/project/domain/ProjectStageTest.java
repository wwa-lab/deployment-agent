package com.wwa.agenthub.agents.project.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectStageTest {

    @Test
    void declaredValues() {
        assertThat(ProjectStage.values()).containsExactly(
                ProjectStage.REQUIREMENT,
                ProjectStage.FUNCTIONAL_DESIGN,
                ProjectStage.TECHNICAL_DESIGN,
                ProjectStage.DEVELOPMENT,
                ProjectStage.TESTING,
                ProjectStage.PERFORMANCE_TEST,
                ProjectStage.RESULT_SIGNOFF,
                ProjectStage.BUSINESS_ENDORSEMENT,
                ProjectStage.CAB,
                ProjectStage.DEPLOYMENT,
                ProjectStage.POST_IMPLEMENTATION);
    }

    @Test
    void fromStringRoundTrip() {
        assertThat(ProjectStage.fromString("RESULT_SIGNOFF")).isEqualTo(ProjectStage.RESULT_SIGNOFF);
    }

    @Test
    void fromStringUnknownThrows() {
        assertThatThrownBy(() -> ProjectStage.fromString("UAT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
