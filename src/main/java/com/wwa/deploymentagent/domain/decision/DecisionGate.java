package com.wwa.deploymentagent.domain.decision;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.enums.ActorKind;
import com.wwa.deploymentagent.domain.task.Task;

/**
 * DecisionGate — MVP Foundation Seam.
 *
 * <p>Single entry point for "should this decision proceed, and under whose
 * authority?". In MVP the only implementation is {@link ManualDecisionGate},
 * which always returns {@link GateOutcome#proceedAsHuman(UserContext)}. The
 * seam exists so that future policy-based or AI-assisted evaluation can be
 * plugged in via composition without touching every call site that currently
 * calls {@link DecisionEngine} directly.
 *
 * <p>Design intent:
 * <ul>
 *   <li>The gate is a pure evaluator. It does not mutate state, does not
 *       persist audit entries, and does not throw on disallowed decisions —
 *       it returns a {@link GateOutcome} that the caller interprets.</li>
 *   <li>State mutation remains in {@link DecisionEngine}, which is where
 *       {@code @Transactional} ownership lives.</li>
 *   <li>The gate's job is to decide (a) whether the action can proceed and
 *       (b) which {@link ActorKind} should be recorded on the resulting
 *       audit and execution history rows.</li>
 * </ul>
 *
 * <p>See {@code docs/04-architecture/architecture.md} §MVP Foundation Seams.
 */
public interface DecisionGate {

    /**
     * Evaluate whether the requested decision should proceed and under whose
     * authority. MVP implementation: always proceed as {@link ActorKind#HUMAN}.
     *
     * @param task     the task the decision applies to
     * @param decision the requested decision type
     * @param user     the logged-in user who submitted the request
     * @return a {@link GateOutcome} describing whether to proceed and the
     *         actor attribution to carry forward
     */
    GateOutcome evaluate(Task task, DecisionType decision, UserContext user);
}
