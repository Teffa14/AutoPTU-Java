package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.ForcedMovementAbilityModifierResolution;
import io.autoptu.core.rules.ForcedMovementInstruction;
import io.autoptu.core.rules.ForcedMovementInstructionResolution;

import java.util.Optional;

/**
 * Runtime-only forced-movement seam for a move whose hit has already been resolved.
 *
 * <p>The ordinary move declaration is revalidated before accuracy/damage execution. By the time
 * Python consumes forced movement, damage outcome bookkeeping has already occurred, so re-running
 * ordinary action-economy validation would reject the same move after its action was spent. This
 * package-private seam therefore accepts only the already-authoritative runtime choice, resolves
 * move metadata again from the server-owned moveset, preserves the Python hit gate, and delegates
 * spatial mutation to the shared forced-displacement engine.</p>
 */
final class RuntimePostHitForcedMovementApplication {
    private RuntimePostHitForcedMovementApplication() {}

    static Optional<RuntimeForcedMovementMoveApplication.Result> apply(
            BattleRuntimeState state,
            MoveChoice choice,
            boolean hit
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (choice == null) throw new IllegalArgumentException("move choice is required");
        if (!hit) return Optional.empty();

        MoveOption move = requireCanonicalMove(state, choice.actorId(), choice.moveId());
        state.requireCombatant(choice.actorId());
        state.requireCombatant(choice.targetId());

        Optional<ForcedMovementInstruction> instruction = ForcedMovementInstructionResolution.resolve(
                move.spec().keywords(),
                move.spec().effectsText()
        );
        RuntimeCombatantState source = state.requireCombatant(choice.actorId());
        String damageCategory = move.combatProfile() == null
                ? ""
                : move.combatProfile().damageCategory();
        instruction = ForcedMovementAbilityModifierResolution.resolve(
                instruction,
                damageCategory,
                source.abilities(),
                source.abilitiesSuppressed()
        );
        if (instruction.isEmpty()) return Optional.empty();

        ForcedDisplacementResolution.Result displacement = ForcedMovementApplication.apply(
                state,
                choice.actorId(),
                choice.targetId(),
                instruction.orElseThrow()
        );
        return Optional.of(new RuntimeForcedMovementMoveApplication.Result(
                move.moveId(), instruction.orElseThrow(), displacement
        ));
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
