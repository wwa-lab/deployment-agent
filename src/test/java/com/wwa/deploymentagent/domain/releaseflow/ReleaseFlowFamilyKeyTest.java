package com.wwa.deploymentagent.domain.releaseflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReleaseFlowFamilyKey")
class ReleaseFlowFamilyKeyTest {

    @Test
    @DisplayName("fromIdentifier keeps stage-prefixed rollout sequence keys")
    void fromIdentifier_stagePrefixedIdentifiers_keepSequence() {
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("sit-projectx-0001")).isEqualTo("projectx0001");
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("uat-projectx-0001")).isEqualTo("projectx0001");
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("prod-projectx-0001")).isEqualTo("projectx0001");
    }

    @Test
    @DisplayName("fromIdentifier normalizes infix stage identifiers to one family key")
    void fromIdentifier_infixStageIdentifiers_shareFamily() {
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("leo-sit-01")).isEqualTo("leo");
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("leo-sit-02")).isEqualTo("leo");
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("leo-uat-01")).isEqualTo("leo");
        assertThat(ReleaseFlowFamilyKey.fromIdentifier("leo-prod-01")).isEqualTo("leo");
    }

    @Test
    @DisplayName("fromStoredRelease prefers release id parsing and falls back to normalized value")
    void fromStoredRelease_prefersReleaseIdThenFallback() {
        assertThat(ReleaseFlowFamilyKey.fromStoredRelease("leo-sit-02", "leo-sit-02")).isEqualTo("leo");
        assertThat(ReleaseFlowFamilyKey.fromStoredRelease(null, "sit01")).isEqualTo("01");
    }
}
