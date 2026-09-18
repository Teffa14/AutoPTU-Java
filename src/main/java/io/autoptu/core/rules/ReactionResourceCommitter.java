package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

import java.util.Objects;

/**
 * Server-authoritative resource boundary for committed reactions.
 *
 * <p>The caller supplies the action type frozen from the reaction source contract.
 * FREE reactions intentionally do not mutate the action budget, matching the pinned
 * Python oracle. Other action types use the ordinary authoritative action budget so
 * reactions cannot bypass base actions, named extras, or standard conversion rules.</p>
 */
public final class ReactionResourceCommitter {

    public boolean canPay(ActionBudget budget, ActionType actionType) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(actionType, "actionType");
        return budget.hasCapacity(actionType);
    }

    public ActionSpendResult commit(ActionBudget budget, ActionType actionType, String detail) {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(actionType, "actionType");
        return budget.consumeDetailed(actionType, detail);
    }
}
