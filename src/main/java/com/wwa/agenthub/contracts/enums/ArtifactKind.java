package com.wwa.agenthub.contracts.enums;

public enum ArtifactKind {
    DOCUMENT,
    MANIFEST,
    PATCH,
    TEST_RESULT,
    REPORT,
    LOG,
    BINARY,
    OTHER,
    // Backward-compatible platform values accepted by existing persisted rows.
    TEXT,
    MARKDOWN,
    JSON,
    PDF,
    IMAGE
}
