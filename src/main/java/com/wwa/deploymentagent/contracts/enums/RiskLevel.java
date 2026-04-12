package com.wwa.deploymentagent.contracts.enums;

/**
 * Risk level classification for a Task — a day-1 seam for staged decision
 * automation under the 7×24 + human-decision constraint.
 *
 * <p>MVP status: every Task defaults to {@link #L2}, which preserves the
 * current "human must decide" behavior. No runtime code reads this field in
 * MVP; it exists so that future policy / AI advisor / SLA-timeout logic can
 * branch on risk level without requiring a schema migration or per-row
 * backfill at that time.
 *
 * <p>Semantic contract (not yet enforced at runtime):
 * <ul>
 *   <li>{@link #L0} — zero-risk, fully reversible. Eligible for full automation.</li>
 *   <li>{@link #L1} — low-risk, reversible. Eligible for policy-based auto-approval
 *       with human-on-the-loop oversight.</li>
 *   <li>{@link #L2} — medium-risk, semi-reversible. Requires human-in-the-loop
 *       decision, but may use SLA timeouts that fall back to a safe state.</li>
 *   <li>{@link #L3} — high-risk, irreversible. Must always be human-in-the-loop,
 *       never auto-approved, never timeout-defaulted. See {@code CLAUDE.md}
 *       §"Decisions that must always be synchronous human-in-the-loop".</li>
 * </ul>
 *
 * <p>See {@code docs/04-architecture/architecture.md} §MVP Foundation Seams.
 */
public enum RiskLevel {
    L0,
    L1,
    L2,
    L3
}
