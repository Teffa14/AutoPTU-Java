package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReactionResourceCommitterTest {
    private final ReactionResourceCommitter committer = new ReactionResourceCommitter();

    @Test
    void freeReactionDoesNotConsumeAnyActionBudget() {
        ActionBudget budget = new ActionBudget();

        assertTrue(committer.canPay(budget, ActionType.FREE));
        ActionSpendResult spend = committer.commit(budget, ActionType.FREE, "Attack of Opportunity");

        assertTrue(spend.consumed());
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
        assertTrue(budget.hasActionAvailable(ActionType.SWIFT));
    }

    @Test
    void nonFreeReactionUsesOrdinaryAuthoritativeBudget() {
        ActionBudget budget = new ActionBudget();

        assertTrue(committer.canPay(budget, ActionType.STANDARD));
        assertTrue(committer.commit(budget, ActionType.STANDARD, "reaction").consumed());
        assertFalse(committer.canPay(budget, ActionType.STANDARD));
        assertFalse(committer.commit(budget, ActionType.STANDARD, "duplicate reaction").consumed());
    }

    @Test
    void namedExtraCanPayReactionAndPreservesOrdinarySpendRules() {
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.SWIFT, "earlier action");
        budget.grantExtra(ActionType.SWIFT, "feature:reaction-grant", 1);

        assertTrue(committer.canPay(budget, ActionType.SWIFT));
        ActionSpendResult spend = committer.commit(budget, ActionType.SWIFT, "triggered feature");

        assertTrue(spend.consumed());
        assertEquals(0, budget.extraCount(ActionType.SWIFT, "feature:reaction-grant"));
        assertFalse(committer.canPay(budget, ActionType.SWIFT));
    }
}
