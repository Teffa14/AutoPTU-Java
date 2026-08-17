package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AutobattlerActionSpace;

import java.util.List;
import java.util.Set;

/**
 * Revalidates a combatant-target move against authoritative runtime state.
 *
 * Controllers and Minecraft adapters may hold a previously legal MoveChoice, but
 * positions, blockers, or action economy can change before execution. This gate
 * recomputes legality from current core state and rejects stale choices before HP
 * or action budget can mutate.
 */
public final class MoveChoiceRevalidation {
    private MoveChoiceRevalidation() {
    }

    public static void requireLegalCombatantMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice is required");
        }
        if (move == null) {
            throw new IllegalArgumentException("move is required");
        }
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("runtime move revalidation currently requires a combatant target");
        }
        if (!choice.moveId().equals(move.moveId())) {
            throw new IllegalArgumentException("move metadata does not match choice moveId");
        }
        if (choice.actionType() != move.actionType()) {
            throw new IllegalArgumentException("move metadata does not match choice action type");
        }

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        TargetCandidate currentTarget = new TargetCandidate(
                target.combatantId(),
                target.position(),
                targetSize
        );

        List<MoveChoice> legal = AutobattlerActionSpace.legalMoveChoices(
                actor.combatantId(),
                actorSize,
                state.grid(),
                actor.position(),
                actor.actionBudget(),
                List.of(move),
                List.of(currentTarget),
                lineOfSightBlockers
        );

        boolean exactChoiceStillLegal = legal.stream()
                .anyMatch(candidate -> candidate.stableKey().equals(choice.stableKey()));
        if (!exactChoiceStillLegal) {
            throw new IllegalArgumentException("move choice is no longer legal in current runtime state");
        }
    }
}
