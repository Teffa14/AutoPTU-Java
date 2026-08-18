package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AutobattlerActionSpace;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Minecraft-facing action-space boundary backed by authoritative runtime state.
 *
 * External adapters provide move/target projections and rendering geometry only.
 * Current position, movement capabilities, and action economy are always read from
 * {@link BattleRuntimeState}; a client or AI cannot submit a forged ActionBudget or
 * MovementProfile to regain actions that PTU already consumed.
 */
public final class RuntimeAutobattlerActionSpace {
    private RuntimeAutobattlerActionSpace() {
    }

    public static List<BattleChoice> legalChoices(
            BattleRuntimeState state,
            String actorId,
            String actorSize,
            List<MoveOption> moves,
            List<TargetCandidate> targetCandidates,
            Set<GridCoord> lineOfSightBlockers,
            int movementPenalty,
            Predicate<GridCoord> canFit
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }

        RuntimeCombatantState actor = state.requireCombatant(actorId);
        return AutobattlerActionSpace.legalChoices(
                actor.combatantId(),
                actorSize,
                state.grid(),
                actor.movementProfile(),
                actor.actionBudget(),
                moves,
                targetCandidates,
                lineOfSightBlockers,
                movementPenalty,
                canFit
        );
    }
}
