package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.rules.ForcedMovementInstruction;
import io.autoptu.core.rules.ForcedMovementInstructionResolution;

import java.util.Optional;

/**
 * Server-owned bridge from a combatant's canonical move metadata to generic Push/Pull execution.
 *
 * <p>Callers identify the acting combatant, target and move. The core resolves the move from the
 * authoritative battle snapshot, derives Python-compatible forced-movement intent from that move,
 * then delegates collision and partial-stop behavior to the shared displacement primitive.</p>
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
            String sourceCombatantId,
            String targetCombatantId,
            String moveId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (sourceCombatantId == null || sourceCombatantId.isBlank()) {
            throw new IllegalArgumentException("sourceCombatantId is required");
        }
        if (targetCombatantId == null || targetCombatantId.isBlank()) {
            throw new IllegalArgumentException("targetCombatantId is required");
        }
        if (moveId == null || moveId.isBlank()) throw new IllegalArgumentException("moveId is required");

        state.requireCombatant(sourceCombatantId);
        state.requireCombatant(targetCombatantId);
        MoveOption move = requireCanonicalMove(state, sourceCombatantId, moveId);
        Optional<ForcedMovementInstruction> instruction = ForcedMovementInstructionResolution.resolve(
                move.spec().keywords(),
                move.spec().effectsText()
        );
        if (instruction.isEmpty()) return Optional.empty();

        ForcedDisplacementResolution.Result displacement = ForcedMovementApplication.apply(
                state,
                sourceCombatantId,
                targetCombatantId,
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
