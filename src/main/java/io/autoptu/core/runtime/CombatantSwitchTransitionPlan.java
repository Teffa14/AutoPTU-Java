package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

/**
 * Language-neutral authoritative state plan for one successful combatant replacement.
 *
 * <p>The plan deliberately separates off-field presence from {@link RuntimeCombatantState}'s
 * movement profile. Python represents a recalled Pokemon with {@code position=None}; Java can
 * materialize that through a dedicated presence store without making every movement rule nullable.</p>
 */
public record CombatantSwitchTransitionPlan(
        String outgoingId,
        String replacementId,
        GridCoord replacementDestination,
        boolean outgoingActiveAfter,
        boolean outgoingOffFieldAfter,
        boolean replacementActiveAfter
) {
    public static CombatantSwitchTransitionPlan resolve(
            BattleRuntimeState state,
            String outgoingId,
            String replacementId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (outgoingId == null || outgoingId.isBlank()) {
            throw new IllegalArgumentException("outgoing combatant is required");
        }
        if (replacementId == null || replacementId.isBlank()) {
            throw new IllegalArgumentException("replacement combatant is required");
        }
        if (outgoingId.equals(replacementId)) {
            throw new IllegalArgumentException("outgoing and replacement combatants must differ");
        }

        RuntimeCombatantState outgoing = state.requireCombatant(outgoingId);
        RuntimeCombatantState replacement = state.requireCombatant(replacementId);
        if (!state.isActive(outgoingId)) {
            throw new IllegalArgumentException("outgoing combatant must be active");
        }
        if (state.isActive(replacementId)) {
            throw new IllegalArgumentException("replacement combatant must be inactive");
        }
        if (replacement.hp() <= 0) {
            throw new IllegalArgumentException("replacement combatant must be conscious");
        }
        if (!state.teamId(outgoingId).equals(state.teamId(replacementId))) {
            throw new IllegalArgumentException("replacement combatant must share the outgoing team");
        }

        return new CombatantSwitchTransitionPlan(
                outgoingId,
                replacementId,
                outgoing.position(),
                false,
                true,
                true
        );
    }
}
