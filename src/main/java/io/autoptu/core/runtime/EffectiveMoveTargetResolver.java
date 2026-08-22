package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.GridState;
import io.autoptu.core.rules.Targeting;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reusable server-authoritative target-selection boundary for area moves.
 *
 * <p>This resolver mirrors the delayed-target contract frozen from the pinned Python
 * oracle: recompute affected tiles from the current move geometry, select combatants by
 * footprint overlap, recheck line of sight, preserve battle insertion order, and move a
 * still-live explicit target to the front. Minecraft/Cobblemon never supplies positions,
 * sizes, blockers, or the resulting target list.</p>
 */
public final class EffectiveMoveTargetResolver {
    private EffectiveMoveTargetResolver() {
    }

    public static EffectiveMoveTargetResolution resolve(
            BattleRuntimeState state,
            String attackerId,
            MoveOption move,
            GridCoord anchor,
            String preferredTargetId
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (anchor == null) throw new IllegalArgumentException("anchor is required");

        RuntimeCombatantState attacker = state.requireCombatant(attackerId);
        GridState grid = new GridState(state.grid().width(), state.grid().height());
        Set<GridCoord> affected = Targeting.affectedTiles(grid, attacker.position(), anchor, move.spec());

        ArrayList<String> targets = new ArrayList<>();
        for (String combatantId : state.combatantIds()) {
            RuntimeCombatantState candidate = state.requireCombatant(combatantId);
            Set<GridCoord> footprint = Targeting.footprintTiles(
                    candidate.position(),
                    state.geometry(combatantId).sizeLabel()
            );
            if (!overlaps(affected, footprint)) {
                continue;
            }
            if (move.requiresLineOfSight() && !Targeting.lineOfSightClear(
                    grid,
                    attacker.position(),
                    candidate.position(),
                    state.grid().blockers()
            )) {
                continue;
            }
            targets.add(combatantId);
        }

        if (preferredTargetId != null && !preferredTargetId.isBlank() && targets.remove(preferredTargetId)) {
            targets.add(0, preferredTargetId);
        }

        return new EffectiveMoveTargetResolution(anchor, List.copyOf(affected), targets);
    }

    private static boolean overlaps(Set<GridCoord> affected, Set<GridCoord> footprint) {
        if (affected.isEmpty() || footprint.isEmpty()) return false;
        Set<GridCoord> smaller = affected.size() <= footprint.size() ? affected : footprint;
        Set<GridCoord> larger = smaller == affected ? footprint : affected;
        for (GridCoord tile : smaller) {
            if (larger.contains(tile)) return true;
        }
        return false;
    }
}
