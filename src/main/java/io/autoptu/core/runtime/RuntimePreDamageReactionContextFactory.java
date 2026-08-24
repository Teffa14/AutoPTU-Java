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
 * <p>Area geometry is derived inside the core from authoritative positions and the effective move.
 * Ordinary combatant-target moves use the defender position as their anchor. TILE/AoE execution
 * supplies the already revalidated authoritative tile anchor so every affected defender observes
 * the same incoming area. Minecraft/Cobblemon never supplies the threatened tiles themselves.</p>
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
        return fromState(
                state,
                attackerId,
                defenderId,
                moveName,
                effectiveMoveName,
                effectiveMove,
                null,
                decisionGate
        );
    }

    public static PreDamageReactionContext fromState(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String effectiveMoveName,
            MoveSpec effectiveMove,
            GridCoord authoritativeAnchor,
            OutOfTurnDecisionGate decisionGate
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (effectiveMove == null) throw new IllegalArgumentException("effectiveMove is required");
        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        RuntimeCombatantState defender = state.requireCombatant(defenderId);
        GridCoord anchor = authoritativeAnchor == null ? defender.position() : authoritativeAnchor;

        List<GridCoord> threatenedTiles = threatenedTiles(
                state,
                attacker.position(),
                anchor,
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
            GridCoord targetAnchor,
            MoveSpec effectiveMove
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (attackerPosition == null) throw new IllegalArgumentException("attackerPosition is required");
        if (targetAnchor == null) throw new IllegalArgumentException("targetAnchor is required");
        if (effectiveMove == null) throw new IllegalArgumentException("effectiveMove is required");
        if (Targeting.normalizedAreaKind(effectiveMove).isEmpty()) return List.of();

        GridState grid = new GridState(state.grid().width(), state.grid().height());
        return List.copyOf(Targeting.affectedTiles(
                grid,
                attackerPosition,
                targetAnchor,
                effectiveMove
        ));
    }
}
