package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Server-owned forced displacement used by PRE-damage reactions such as Sway.
 *
 * <p>The pinned Python Sway hook considers the eight tiles around the defender, filters
 * out-of-bounds tiles, blockers, and tiles occupied by another living combatant, then chooses
 * the lexicographically first remaining coordinate. Minecraft/Cobblemon never supplies the
 * destination.</p>
 */
public final class ReactionPushApplication {
    private ReactionPushApplication() {
    }

    public static Optional<GridCoord> pushToFirstOpenAdjacent(
            BattleRuntimeState state,
            String centerId,
            String pushedId
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState center = state.requireCombatant(centerId);
        RuntimeCombatantState pushed = state.requireCombatant(pushedId);
        GridCoord origin = center.position();

        List<GridCoord> candidates = new ArrayList<>(List.of(
                new GridCoord(origin.x() + 1, origin.y()),
                new GridCoord(origin.x() - 1, origin.y()),
                new GridCoord(origin.x(), origin.y() + 1),
                new GridCoord(origin.x(), origin.y() - 1),
                new GridCoord(origin.x() + 1, origin.y() + 1),
                new GridCoord(origin.x() + 1, origin.y() - 1),
                new GridCoord(origin.x() - 1, origin.y() + 1),
                new GridCoord(origin.x() - 1, origin.y() - 1)
        ));
        candidates.removeIf(coord -> !state.grid().inBounds(coord));
        candidates.removeIf(state.grid()::isBlocker);
        candidates.removeIf(coord -> occupiedByOtherLivingCombatant(state, coord, centerId, pushedId));
        candidates.sort(Comparator.comparingInt(GridCoord::x).thenComparingInt(GridCoord::y));
        if (candidates.isEmpty()) return Optional.empty();

        GridCoord destination = candidates.getFirst();
        pushed.moveTo(destination);
        return Optional.of(destination);
    }

    private static boolean occupiedByOtherLivingCombatant(
            BattleRuntimeState state,
            GridCoord coord,
            String centerId,
            String pushedId
    ) {
        for (String combatantId : state.combatantIds()) {
            if (combatantId.equals(centerId) || combatantId.equals(pushedId)) continue;
            RuntimeCombatantState combatant = state.requireCombatant(combatantId);
            if (combatant.hp() > 0 && combatant.position().equals(coord)) return true;
        }
        return false;
    }
}
