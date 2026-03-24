package com.wwa.deploymentagent.contracts.enums;

public enum PermissionKey {
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
    AUDIT_VIEW("audit.view"),
    ACCESS_MANAGE("access.manage");

    private final String value;

    PermissionKey(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
