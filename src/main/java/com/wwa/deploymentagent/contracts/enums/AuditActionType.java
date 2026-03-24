package com.wwa.deploymentagent.contracts.enums;

/** Audit action types – append-only registry. */
public enum AuditActionType {
    upload,
    edit,
    view_result,
    approve,
    reject,
    rerun,
    skip,
    config_update,
    auto_submit,
    request_start,
    request_fail,
    request_archive,
    request_restore,
    request_purge,
    access_grant_create,
    access_grant_update,
    access_grant_suspend,
    access_grant_reactivate
}
