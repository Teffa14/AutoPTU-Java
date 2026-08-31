package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.ForcedMovementInstruction;
import io.autoptu.core.rules.ForcedMovementInstructionResolution;

import java.util.Optional;
import java.util.Set;

/**
 * Server-owned bridge from a currently legal combatant-target move to generic Push/Pull execution.
 *
 * <p>The caller supplies the declared move choice, but the core resolves canonical move metadata
 * and revalidates the complete actor/target/move choice against current battle state before any
 * displacement can mutate position. This prevents adapters or stale controllers from using an
 * owned Push/Pull move to displace an arbitrary combatant.</p>
 */
public final class RuntimeForcedMovementMoveApplication {
    private RuntimeForcedMovementMoveApplication() {}

    public record Result(
            String moveId,
            ForcedMovementInstruction instruction,
            ForcedDisplacementResolution.Result displacement
    ) {
        public Result {
            if (moveId == null || moveId.isBlank()) throw new IllegalArgumentException("moveId is required");
            if (instruction == null) throw new IllegalArgumentException("instruction is required");
            if (displacement == null) throw new IllegalArgumentException("displacement is required");
        }
    }

    public static Optional<Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        if (lineOfSightBlockers == null) throw new IllegalArgumentException("lineOfSightBlockers is required");

        MoveOption move = requireCanonicalMove(state, choice.actorId(), choice.moveId());
        MoveChoiceRevalidation.requireLegalCombatantMove(
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers
        );

        Optional<ForcedMovementInstruction> instruction = ForcedMovementInstructionResolution.resolve(
                move.spec().keywords(),
                move.spec().effectsText()
        );
        if (instruction.isEmpty()) return Optional.empty();

        ForcedDisplacementResolution.Result displacement = ForcedMovementApplication.apply(
                state,
                choice.actorId(),
                choice.targetId(),
                instruction.orElseThrow()
        );
        return Optional.of(new Result(move.moveId(), instruction.orElseThrow(), displacement));
    }

    private static MoveOption requireCanonicalMove(
            BattleRuntimeState state,
            String sourceCombatantId,
            String moveId
    ) {
        if (!state.hasCanonicalMoves(sourceCombatantId)) {
            throw new IllegalStateException("combatant has no canonical moveset: " + sourceCombatantId);
        }
        for (MoveOption move : state.moveOptions(sourceCombatantId)) {
            if (move.moveId().equals(moveId)) return move;
        }
        throw new IllegalArgumentException(
                "move is not in authoritative combatant moveset: " + sourceCombatantId + "/" + moveId
        );
    }
}
