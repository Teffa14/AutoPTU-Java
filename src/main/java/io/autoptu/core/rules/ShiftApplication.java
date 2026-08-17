package io.autoptu.core.rules;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.ShiftApplicationResult;

import java.util.Set;

/** Applies a previously generated legal Shift choice and emits its semantic result. */
public final class ShiftApplication {
    private ShiftApplication() {}

    public static ShiftApplicationResult apply(String actorId, GridCoord origin, GridCoord destination,
            Set<GridCoord> legalDestinations, ActionBudget budget) {
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (origin == null) throw new IllegalArgumentException("origin is required");
        if (destination == null) throw new IllegalArgumentException("destination is required");
        if (budget == null) throw new IllegalArgumentException("budget is required");
        if (origin.equals(destination)) throw new IllegalArgumentException("shift destination must differ from origin");
        Set<GridCoord> legal = legalDestinations == null ? Set.of() : legalDestinations;
        if (!legal.contains(destination)) throw new IllegalArgumentException("shift destination is not legal");
        if (!budget.consume(ActionType.SHIFT, "shift:" + destination.x() + "," + destination.y())) {
            throw new IllegalStateException("shift action is unavailable");
        }
        ShiftResolvedEvent event = new ShiftResolvedEvent(actorId, origin, destination);
        return new ShiftApplicationResult(destination, event);
    }
}
