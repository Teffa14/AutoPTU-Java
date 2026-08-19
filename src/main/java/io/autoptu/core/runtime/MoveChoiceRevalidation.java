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
 * execution. Declared actions recompute all of those resources. Triggered effects
 * deliberately ignore the actor's turn/frequency resources while still recomputing
 * current target geometry, range and line of sight.
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
        requireLegalCombatantMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                MoveExecutionMode.DECLARED_ACTION
        );
    }

    /**
     * Revalidate a server-triggered move without charging the source combatant another
     * action or frequency use. This matches Python delayed/reaction-style execution,
     * which calls the move resolver directly instead of queuing another UseMoveAction.
     */
    public static void requireLegalTriggeredCombatantMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        requireLegalCombatantMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                MoveExecutionMode.TRIGGERED_EFFECT
        );
    }

    private static void requireLegalCombatantMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            MoveExecutionMode mode
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
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
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
        if (mode == MoveExecutionMode.DECLARED_ACTION && !actor.moveFrequencyUsage().available(move)) {
            throw new IllegalArgumentException("move frequency is exhausted in current runtime state");
        }
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        TargetCandidate currentTarget = new TargetCandidate(
                target.combatantId(),
                target.position(),
                targetSize
        );

        ActionBudget legalityBudget = mode == MoveExecutionMode.DECLARED_ACTION
                ? actor.actionBudget()
                : new ActionBudget();
        List<MoveChoice> legal = AutobattlerActionSpace.legalMoveChoices(
                actor.combatantId(),
                actorSize,
                state.grid(),
                actor.position(),
                legalityBudget,
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
