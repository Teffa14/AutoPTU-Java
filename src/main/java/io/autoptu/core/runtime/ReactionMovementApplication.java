package io.autoptu.core.runtime;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.Movement;
import io.autoptu.core.rules.ReactionEscapeMovementResolution;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Server-authoritative application boundary for reaction movement.
 *
 * <p>The caller supplies only the already-resolved threatened area and an optional
 * reaction-specific displacement cap. Reachability comes from the canonical battle
 * grid and combatant movement profile. The selected destination is committed to the
 * runtime without consuming normal SHIFT action economy; the triggering reaction owns
 * any usage/permission bookkeeping.</p>
 */
public final class ReactionMovementApplication {
    private ReactionMovementApplication() {
    }

    public static AppliedActionResult escapeThreatenedArea(
            BattleRuntimeState state,
            String actorId,
            Collection<GridCoord> threatenedTiles,
            Integer maxDistance,
            Predicate<GridCoord> canFit
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");

        RuntimeCombatantState actor = state.requireCombatant(actorId);
        GridCoord origin = actor.position();
        var reachable = Movement.legalShiftTiles(state.grid(), actor.movementProfile(), 0, canFit);
        Optional<GridCoord> destination = ReactionEscapeMovementResolution.chooseDestination(
                origin,
                reachable,
                threatenedTiles,
                maxDistance
        );
        if (destination.isEmpty()) {
            return new AppliedActionResult(List.of());
        }

        GridCoord resolvedDestination = destination.orElseThrow();
        actor.moveTo(resolvedDestination);
        return new AppliedActionResult(List.of(
                new ShiftResolvedEvent(actor.combatantId(), origin, resolvedDestination)
        ));
    }

    public static AppliedActionResult escapeThreatenedArea(
            BattleRuntimeState state,
            String actorId,
            Collection<GridCoord> threatenedTiles,
            Integer maxDistance
    ) {
        return escapeThreatenedArea(state, actorId, threatenedTiles, maxDistance, ignored -> true);
    }
}
