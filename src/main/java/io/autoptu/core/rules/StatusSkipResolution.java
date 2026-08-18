package io.autoptu.core.rules;

import io.autoptu.core.model.ActionType;

/**
 * Base PTU status-skip action consumption extracted from Python StatusController.
 *
 * A status skip marks the base STANDARD and SHIFT buckets as spent when they are
 * still available. It deliberately does not consume extra actions: Python calls
 * mark_action directly for these buckets rather than the normal action resolver.
 */
public final class StatusSkipResolution {
    private static final String DETAIL = "Status skip";

    private StatusSkipResolution() {}

    public static void apply(ActionBudget budget) {
        if (budget == null) {
            throw new IllegalArgumentException("budget is required");
        }
        if (budget.hasActionAvailable(ActionType.STANDARD)) {
            budget.markAction(ActionType.STANDARD, DETAIL);
        }
        if (budget.hasActionAvailable(ActionType.SHIFT)) {
            budget.markAction(ActionType.SHIFT, DETAIL);
        }
    }
}
