package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.ForcedMovementInstruction;

/** Applies Push/Pull instructions through the shared forced-displacement resolver. */
public final class ForcedMovementApplication {
    private ForcedMovementApplication() {}

    public static ForcedDisplacementResolution.Result apply(
            BattleRuntimeState state,
            String sourceCombatantId,
            String targetCombatantId,
            ForcedMovementInstruction instruction
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (instruction == null) throw new IllegalArgumentException("forced movement instruction is required");

        RuntimeCombatantState source = state.requireCombatant(sourceCombatantId);
        RuntimeCombatantState target = state.requireCombatant(targetCombatantId);

        GridCoord away = direction(source.position(), target.position());
        GridCoord direction = instruction.kind() == ForcedMovementInstruction.Kind.PUSH
                ? away
                : new GridCoord(-away.x(), -away.y());

        return ForcedDisplacementResolution.resolve(state, targetCombatantId, direction, instruction.distance());
    }

    private static GridCoord direction(GridCoord source, GridCoord target) {
        return new GridCoord(
                Integer.compare(target.x() - source.x(), 0),
                Integer.compare(target.y() - source.y(), 0)
        );
    }
}
