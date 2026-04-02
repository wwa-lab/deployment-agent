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
    config_delete,
    auto_submit,
    request_start,
    request_fail,
    request_archive,
    request_restore,
    request_purge,
    access_grant_create,
    access_grant_update,
    access_grant_suspend,
    access_grant_reactivate,
    development_spec_create,
    development_spec_update,
    development_spec_generate,
    development_spec_export
}
