package io.autoptu.core.hook;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.rules.ActionSpendResult;

/**
 * Immutable instruction emitted after an action-window candidate has been revalidated,
 * paid, and claimed by the authoritative core.
 *
 * <p>This record carries execution provenance only. Downstream execution must not use it
 * to re-decide trigger legality or action-economy payment.</p>
 */
public record CommittedActionWindowInstruction(
        String reactingCombatantId,
        String actionKey,
        String triggerKey,
        ActionType actionType,
        String resourceDetail,
        ActionSpendResult spend
) {
    public CommittedActionWindowInstruction {
        reactingCombatantId = requireText(reactingCombatantId, "reacting combatant id");
        actionKey = requireText(actionKey, "action key");
        triggerKey = requireText(triggerKey, "trigger key");
        if (actionType == null) {
            throw new IllegalArgumentException("action type is required");
        }
        resourceDetail = resourceDetail == null ? "" : resourceDetail.strip();
        if (spend == null || !spend.consumed()) {
            throw new IllegalArgumentException("committed instruction requires a consumed spend");
        }
    }

    public static CommittedActionWindowInstruction from(
            ActionWindowCandidate candidate,
            ActionWindowResourceCommit resource,
            ActionSpendResult spend
    ) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate is required");
        }
        if (resource == null) {
            throw new IllegalArgumentException("resource is required");
        }
        return new CommittedActionWindowInstruction(
                candidate.reactingCombatantId(),
                candidate.actionKey(),
                candidate.triggerKey(),
                resource.actionType(),
                resource.detail(),
                spend
        );
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.strip();
    }
}
