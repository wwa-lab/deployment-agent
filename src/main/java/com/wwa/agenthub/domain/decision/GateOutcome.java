package com.wwa.agenthub.domain.decision;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.ActorKind;

/**
 * GateOutcome — result returned by {@link DecisionGate#evaluate}.
 *
 * <p>MVP Foundation Seam: only {@link #proceedAsHuman(UserContext)} is ever
 * produced today. The record's shape reserves fields for future policy and
 * AI-assisted outcomes so callers (and audit writes) do not need to change
 * when those paths are introduced.
 *
 * @param allowed    whether the caller should proceed with the decision. MVP
 *                   always returns {@code true}; a future {@code PolicyDecisionGate}
 *                   may return {@code false} with a {@code reason} instead of
 *                   throwing, so the caller can render a friendly message.
 * @param actorKind  the actor to attribute the resulting audit and history
 *                   rows to. Always {@link ActorKind#HUMAN} in MVP.
 * @param actorRef   opaque reference for non-human actors (e.g. {@code policy:<id>},
 *                   {@code ai:<model>#<session>}). Null for {@link ActorKind#HUMAN}.
 * @param reason     human-readable explanation, mainly used when {@code allowed}
 *                   is false. Null in MVP.
 */
public record GateOutcome(
        boolean allowed,
        ActorKind actorKind,
        String actorRef,
        String reason
) {
    /**
     * Standard MVP outcome: proceed with full authority, attributed to the
     * logged-in human operator. The {@code user} parameter is accepted so the
     * signature is symmetric with future overloads and makes the human
     * attribution explicit at each call site.
     */
    public static GateOutcome proceedAsHuman(UserContext user) {
        return new GateOutcome(true, ActorKind.HUMAN, null, null);
    }
}
