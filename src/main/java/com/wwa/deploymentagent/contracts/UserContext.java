package com.wwa.deploymentagent.contracts;

/**
 * Authenticated user identity injected from WWA platform headers.
 * Populated by HeaderAuthFilter from X-User-Id and X-User-Role headers.
 */
public record UserContext(String userId, String role) {}
