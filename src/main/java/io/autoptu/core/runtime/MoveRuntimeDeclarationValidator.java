package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;

import java.util.Set;

/**
 * Dispatches authoritative move declaration validation from explicit execution identity.
 * Resource ownership must not be used to infer whether a move is ordinary, area-resolved,
 * delayed, or an already-validated committed reaction.
 */
final class MoveRuntimeDeclarationValidator {
    private MoveRuntimeDeclarationValidator() {}

    static void requireValid(
            MoveRuntimeExecutionMode mode,
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        if (mode == null) throw new IllegalArgumentException("execution mode is required");
        switch (mode.declarationValidation()) {
            case ORDINARY -> MoveChoiceRevalidation.requireLegalCombatantMove(
                    state, choice, move, actorSize, targetSize, lineOfSightBlockers);
            case AREA_RESOLVED -> requireAreaResolvedCombatantChoice(state, choice, move);
            case DELAYED -> requireDelayedCombatantChoice(state, choice, move);
            case ALREADY_VALIDATED -> requireBoundCombatants(state, choice, move);
        }
    }

    private static void requireBoundCombatants(BattleRuntimeState state, MoveChoice choice, MoveOption move) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("already-validated move execution requires a combatant target");
        }
        if (!choice.moveId().equals(move.moveId())) {
            throw new IllegalArgumentException("move metadata does not match committed choice moveId");
        }
        state.requireCombatant(choice.actorId());
        state.requireCombatant(choice.targetId());
    }

    private static void requireDelayedCombatantChoice(BattleRuntimeState state, MoveChoice choice, MoveOption move) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("delayed move execution currently requires a combatant target");
        }
        if (!choice.moveId().equals(move.moveId())) {
            throw new IllegalArgumentException("move metadata does not match delayed choice moveId");
        }
        state.requireCombatant(choice.actorId());
        state.requireCombatant(choice.targetId());
    }

    private static void requireAreaResolvedCombatantChoice(BattleRuntimeState state, MoveChoice choice, MoveOption move) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("area move target execution requires a combatant target");
        }
        if (!choice.moveId().equals(move.moveId()) || choice.actionType() != move.actionType()) {
            throw new IllegalArgumentException("move metadata does not match area target choice");
        }
        state.requireCombatant(choice.actorId());
        state.requireCombatant(choice.targetId());
    }
}
