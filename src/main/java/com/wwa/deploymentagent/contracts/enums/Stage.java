package com.wwa.deploymentagent.contracts.enums;

/** Deployment stages in progression order: SIT → UAT → PROD. */
public enum Stage {
    SIT,
    UAT,
    PROD;

    /** Returns the next stage in SIT→UAT→PROD order, or null if already PROD. */
    public Stage next() {
        Stage[] values = Stage.values();
        int nextIdx = this.ordinal() + 1;
        return nextIdx < values.length ? values[nextIdx] : null;
    }
}
