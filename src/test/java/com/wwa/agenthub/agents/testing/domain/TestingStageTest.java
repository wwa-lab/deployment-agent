package com.wwa.agenthub.agents.testing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestingStageTest {

    @Test
    void declaredValues() {
        assertThat(TestingStage.values()).containsExactly(TestingStage.UAT);
    }

    @Test
    void fromStringRoundTrip() {
        assertThat(TestingStage.fromString("UAT")).isEqualTo(TestingStage.UAT);
    }

    @Test
    void fromStringUnknownThrows() {
        assertThatThrownBy(() -> TestingStage.fromString("SIT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
