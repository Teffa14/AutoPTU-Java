package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.rules.Targeting;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Server-owned footprint/collision legality matching Python BattleState._position_can_fit.
 *
 * Minecraft/Cobblemon may project positions and model scale, but PTU landing legality is
 * resolved exclusively from the authoritative battle snapshot.
 */
public final class RuntimePositionFitResolver {
    private static final Set<String> BLOCKING_TILE_TYPES = Set.of("wall", "blocker", "blocking", "void");

    private RuntimePositionFitResolver() {
    }

    /** Python default semantics: exclude self, require other occupants active and conscious, block terrain. */
    public static boolean canFit(BattleRuntimeState state, String actorId, GridCoord destination) {
        return canFit(state, actorId, destination, Options.defaults(actorId));
    }

    static boolean canFit(
            BattleRuntimeState state,
            String actorId,
            GridCoord destination,
            Options options
    ) {
        if (state == null || actorId == null || actorId.isBlank() || destination == null || options == null) {
            return false;
        }
        if (!state.combatants().containsKey(actorId)) {
            return false;
        }

        Set<GridCoord> candidateTiles = Targeting.footprintTiles(
                destination,
                state.geometry(actorId).sizeLabel()
        );
        MovementGrid grid = state.grid();
        for (GridCoord tile : candidateTiles) {
            if (!grid.inBounds(tile)) {
                return false;
            }
            if (options.blockOnTerrain()) {
                if (grid.isBlocker(tile)) {
                    return false;
                }
                String tileType = grid.tileType(tile) == null
                        ? ""
                        : grid.tileType(tile).strip().toLowerCase(Locale.ROOT);
                if (BLOCKING_TILE_TYPES.contains(tileType)) {
                    return false;
                }
            }
        }

        Set<GridCoord> occupied = new LinkedHashSet<>();
        for (String otherId : state.combatantIds()) {
            if (options.includeIds() != null && !options.includeIds().contains(otherId)) {
                continue;
            }
            if (options.excludeId() != null && options.excludeId().equals(otherId)) {
                continue;
            }
            if (options.activeOnly() && !state.isActive(otherId)) {
                continue;
            }
            RuntimeCombatantState other = state.requireCombatant(otherId);
            if (options.consciousOnly() && other.hp() <= 0) {
                continue;
            }
            occupied.addAll(Targeting.footprintTiles(
                    other.position(),
                    state.geometry(otherId).sizeLabel()
            ));
        }
        for (GridCoord tile : candidateTiles) {
            if (occupied.contains(tile)) {
                return false;
            }
        }
        return true;
    }

    record Options(
            Set<String> includeIds,
            String excludeId,
            boolean activeOnly,
            boolean consciousOnly,
            boolean blockOnTerrain
    ) {
        Options {
            includeIds = includeIds == null ? null : Set.copyOf(includeIds);
        }

        static Options defaults(String actorId) {
            return new Options(null, actorId, true, true, true);
        }
    }
}
