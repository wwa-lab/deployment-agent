package com.wwa.deploymentagent.contracts.enums;

/**
 * Actor kind — who or what produced an audit / decision / execution record.
 *
 * <p>MVP status: every write is {@link #HUMAN}. The other values are reserved as
 * day-1 seams so that future policy-based auto-approval, AI-assisted suggestions,
 * and system-initiated actions can be recorded without retrofitting immutable
 * history tables.
 *
 * <p>Once a row is written, its actor kind is permanent — this is the core
 * reason the field must exist from day one even though only {@code HUMAN} is
 * ever written today. See {@code docs/04-architecture/architecture.md}
 * section "MVP Foundation Seams for 7×24 + human decisions".
 */
public enum ActorKind {

    /** A logged-in human operator pressed a button or submitted a request. */
    HUMAN,

    /**
     * A pre-authorized decision policy evaluated the situation and took the
     * action without a runtime human click. The policy itself was authored and
     * approved by a human at an earlier time.
     */
    POLICY,

    /**
     * A human pressed the button, but the suggestion was produced or ranked by
     * an AI advisor. The human is still the authoritative decider.
     */
    AI_ASSISTED,

    /**
     * The platform itself produced the record (scheduled job, timeout sweeper,
     * callback handler, etc.) with no direct human or policy attribution.
     */
    SYSTEM
}
