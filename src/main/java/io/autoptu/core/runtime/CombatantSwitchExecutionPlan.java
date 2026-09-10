package io.autoptu.core.runtime;

import java.util.List;

/**
 * Ordered server-authoritative switch transaction contract.
 *
 * <p>This plan composes the already frozen transition and entry-state contracts without
 * mutating battle state. The order mirrors the pinned Python {@code _apply_switch()} prefix:
 * activity/presence change first, temporary entry markers next, then entry hooks.</p>
 */
public record CombatantSwitchExecutionPlan(
        CombatantSwitchTransitionPlan transition,
        CombatantSwitchEntryStatePlan entryState,
        List<Stage> stages
) {
    public CombatantSwitchExecutionPlan {
        if (transition == null) throw new IllegalArgumentException("switch transition is required");
        if (entryState == null) throw new IllegalArgumentException("switch entry state is required");
        if (!transition.equals(entryState.transition())) {
            throw new IllegalArgumentException("switch plans must describe the same transition");
        }
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public static CombatantSwitchExecutionPlan resolve(
            BattleRuntimeState state,
            String outgoingId,
            String replacementId
    ) {
        CombatantSwitchTransitionPlan transition = CombatantSwitchTransitionPlan.resolve(
                state, outgoingId, replacementId
        );
        CombatantSwitchEntryStatePlan entryState = CombatantSwitchEntryStatePlan.resolve(state, transition);
        return new CombatantSwitchExecutionPlan(
                transition,
                entryState,
                List.of(
                        Stage.DEACTIVATE_OUTGOING,
                        Stage.REMOVE_OUTGOING_PRESENCE,
                        Stage.ACTIVATE_REPLACEMENT,
                        Stage.PLACE_REPLACEMENT,
                        Stage.ADD_RELEASED_FROM_BALL,
                        Stage.ADD_JOINED_ROUND,
                        Stage.DISPATCH_COMBATANT_ENTRY
                )
        );
    }

    public enum Stage {
        DEACTIVATE_OUTGOING,
        REMOVE_OUTGOING_PRESENCE,
        ACTIVATE_REPLACEMENT,
        PLACE_REPLACEMENT,
        ADD_RELEASED_FROM_BALL,
        ADD_JOINED_ROUND,
        DISPATCH_COMBATANT_ENTRY
    }
}
