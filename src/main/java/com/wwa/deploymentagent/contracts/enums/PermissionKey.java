package com.wwa.deploymentagent.contracts.enums;

public enum PermissionKey {
    // --- Platform-level permissions (WWA shell, shared capabilities) ---
    /** Grants entry into the WWA platform. Future gate for platform-level access checks. */
    PLATFORM_ENTER("platform.enter"),
    /** Grants the ability to manage platform-level access grants (WWA Access Management page). */
    PLATFORM_ACCESS_MANAGE("platform.access.manage"),
    /** Grants visibility of the platform-level audit log view. */
    PLATFORM_AUDIT_VIEW("platform.audit.view"),

    // --- Deployment Agent workspace permissions (agent-private) ---
    RELEASE_VIEW("release.view"),
    RELEASE_UPLOAD("release.upload"),
    RELEASE_VIEW_ARCHIVED("release.view_archived"),
    RELEASE_RUNDOWN_EDIT("release.rundown.edit"),
    RELEASE_RUNDOWN_ARCHIVE("release.rundown.archive"),
    RELEASE_RUNDOWN_RESTORE("release.rundown.restore"),
    RELEASE_RUNDOWN_PURGE("release.rundown.purge"),
    RELEASE_RUNDOWN_START("release.rundown.start"),
    RELEASE_RUNDOWN_FAIL("release.rundown.fail"),
    TASK_EDIT("task.edit"),
    TASK_RUN("task.run"),
    TASK_REVIEW("task.review"),
    CONFIG_MANAGE("config.manage"),
    /** @deprecated Use PLATFORM_AUDIT_VIEW for platform-level audit access. Kept for backward compatibility. */
    AUDIT_VIEW("audit.view"),
    /** @deprecated Use PLATFORM_ACCESS_MANAGE for platform-level access management. Kept for backward compatibility. */
    ACCESS_MANAGE("access.manage");

    private final String value;

    PermissionKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
