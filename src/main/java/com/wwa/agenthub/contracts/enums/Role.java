package com.wwa.agenthub.contracts.enums;

/** RBAC roles aligned with the domain design. */
public enum Role {
    DEVELOPER,
    TL,
    DEVOPS_ADMIN,
    AUDIT,
    MANAGEMENT,
    /**
     * Anonymous read-only viewer. Granted automatically by the guest login
     * endpoint without an AccessGrant. Holders can browse every page, but
     * backend write operations are blocked by GuestReadOnlyFilter.
     */
    GUEST
}
