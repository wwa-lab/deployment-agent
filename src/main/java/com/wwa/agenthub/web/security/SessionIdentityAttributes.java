package com.wwa.agenthub.web.security;

/** Shared session keys for distinguishing synthetic guest identity from grants. */
public final class SessionIdentityAttributes {

    public static final String USER_CONTEXT = "USER_CONTEXT";
    public static final String SYNTHETIC_GUEST = "SYNTHETIC_GUEST_SESSION";

    private SessionIdentityAttributes() {
    }
}
