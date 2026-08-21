package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.TargetCandidate;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.AutobattlerActionSpace;

import java.util.List;
import java.util.Set;

/**
 * Revalidates a combatant-target move against authoritative runtime state.
 *
 * Controllers and Minecraft adapters may hold a previously legal MoveChoice, but
 * positions, blockers, action economy, or move-frequency usage can change before
 * execution. Ordinary submitted actions must still own those resource checks.
 * Server-owned delayed triggers instead revalidate current spatial/target legality
 * with a fresh action budget because their action/frequency cost was already paid
 * when the delayed effect was scheduled.
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
        RuntimeCombatantState actor = requireBaseCombatantMoveState(state, choice, move);
        if (!actor.moveFrequencyUsage().available(move)) {
            throw new IllegalArgumentException("move frequency is exhausted in current runtime state");
        }
        requireSpatiallyLegal(
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers,
                actor.actionBudget()
        );
    }

    /**
     * Revalidate a mature delayed attack without re-charging ordinary action/frequency resources.
     * Target identity, current position, range, footprint and LoS are still authoritative.
     */
    public static void requireLegalDelayedCombatantMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        requireBaseCombatantMoveState(state, choice, move);
        requireSpatiallyLegal(
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers,
                new ActionBudget()
        );
    }

    private static RuntimeCombatantState requireBaseCombatantMoveState(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move
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
        state.requireCombatant(choice.targetId());
        return actor;
    }

    private static void requireSpatiallyLegal(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            ActionBudget actionBudget
    ) {
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
                actionBudget,
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
