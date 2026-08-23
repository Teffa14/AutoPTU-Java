package io.autoptu.core.runtime;

import io.autoptu.core.hook.OutOfTurnDecisionGate;
import io.autoptu.core.hook.PreDamageReactionContext;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.GridState;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.rules.Targeting;

import java.util.List;

/**
 * Builds PRE-damage reaction context from canonical battle state.
 *
 * <p>Area geometry is derived inside the core from the current attacker/defender
 * positions and the effective move. Minecraft/Cobblemon may request an action, but it
 * does not supply the threatened tiles used by Telepathy/Perception-style reactions.</p>
 */
public final class RuntimePreDamageReactionContextFactory {
    private RuntimePreDamageReactionContextFactory() {
    }

    public static PreDamageReactionContext fromState(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String effectiveMoveName,
            MoveSpec effectiveMove,
            OutOfTurnDecisionGate decisionGate
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (effectiveMove == null) throw new IllegalArgumentException("effectiveMove is required");
        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState defender = state.requireCombatant(defenderId);

        List<GridCoord> threatenedTiles = threatenedTiles(
                state,
                attacker.position(),
                defender.position(),
                effectiveMove
        );
        return new PreDamageReactionContext(
                state,
                attackerId,
                defenderId,
                moveName,
                effectiveMoveName,
                threatenedTiles,
                decisionGate
        );
    }

    public static List<GridCoord> threatenedTiles(
            BattleRuntimeState state,
            GridCoord attackerPosition,
            GridCoord defenderPosition,
            MoveSpec effectiveMove
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (attackerPosition == null) throw new IllegalArgumentException("attackerPosition is required");
        if (defenderPosition == null) throw new IllegalArgumentException("defenderPosition is required");
        if (effectiveMove == null) throw new IllegalArgumentException("effectiveMove is required");
        if (Targeting.normalizedAreaKind(effectiveMove).isEmpty()) return List.of();

        GridState grid = new GridState(state.grid().width(), state.grid().height());
        return List.copyOf(Targeting.affectedTiles(
                grid,
                attackerPosition,
                defenderPosition,
                effectiveMove
        ));
    }
}
