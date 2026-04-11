package com.wwa.deploymentagent.domain.decision;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.domain.task.Task;
import org.springframework.stereotype.Component;

/**
 * ManualDecisionGate — MVP implementation of {@link DecisionGate}.
 *
 * <p>Always returns {@link GateOutcome#proceedAsHuman(UserContext)}. This is a
 * pure seam with zero runtime effect in MVP. State validation, audit writes
 * and any transition rejections remain the responsibility of
 * {@link DecisionEngine}. This class exists only so that future
 * {@code PolicyDecisionGate} and {@code AiAdvisedDecisionGate} implementations
 * can be introduced via composition without touching call sites.
 *
 * <p>See {@code docs/04-architecture/architecture.md} §MVP Foundation Seams.
 */
@Component
public class ManualDecisionGate implements DecisionGate {

    @Override
    public GateOutcome evaluate(Task task, DecisionType decision, UserContext user) {
        return GateOutcome.proceedAsHuman(user);
    }
}
