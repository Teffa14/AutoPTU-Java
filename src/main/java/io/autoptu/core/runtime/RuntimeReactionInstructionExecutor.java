package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.random.PythonRandom;

import java.util.Objects;
import java.util.Set;

/**
 * Executes a committed reaction instruction through the ordinary authoritative move pipeline.
 *
 * <p>The reaction window already owns trigger eligibility and once-per-round consumption. This
 * boundary converts that committed instruction into a FREE move declaration so reaction execution
 * cannot consume the reactor's ordinary Standard/Shift/Swift inventory. Targeting, RNG, damage,
 * hooks, move frequency and semantic events remain owned by the existing core move resolver.</p>
 */
public final class RuntimeReactionInstructionExecutor {
    private RuntimeReactionInstructionExecutor() {
    }

    public static AppliedActionResult execute(
            BattleRuntimeState state,
            RuntimeReactionInstruction instruction,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            BattleRuntimeDependencies dependencies
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(instruction, "instruction");
        Objects.requireNonNull(rng, "rng");
        Objects.requireNonNull(legacyInput, "legacyInput");
        Objects.requireNonNull(dependencies, "dependencies");

        if (instruction.round() != state.currentRound()) {
            throw new IllegalStateException("reaction instruction is no longer in the current round");
        }
        RuntimeCombatantState reactor = state.requireCombatant(instruction.reactorId());
        RuntimeCombatantState target = state.requireCombatant(instruction.targetCombatantId());
        if (reactor.hp() <= 0) {
            throw new IllegalStateException("reaction executor cannot act while fainted");
        }
        if (target.hp() <= 0 || !state.isActive(instruction.targetCombatantId())) {
            throw new IllegalStateException("reaction target is no longer targetable");
        }

        GridCoord targetAnchor = target.position();
        MoveChoice choice = new MoveChoice(
                instruction.reactorId(),
                instruction.move().moveId(),
                ChoiceTargetMode.COMBATANT,
                instruction.targetCombatantId(),
                targetAnchor,
                ActionType.FREE
        );

        return RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice,
                instruction.move(),
                state.geometry(instruction.reactorId()).sizeLabel(),
                state.geometry(instruction.targetCombatantId()).sizeLabel(),
                Set.of(),
                "Reaction:" + instruction.reactionKey(),
                rng,
                legacyInput,
                false,
                false,
                dependencies
        );
    }
}
