package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Server-owned initiative order and cursor used by stateful battle rules.
 *
 * Python keeps initiative_order plus _initiative_index on BattleState. Minecraft and
 * Cobblemon may render that order, but rules such as Analytic must read the canonical
 * battle snapshot rather than client/controller claims.
 */
public final class InitiativeProgressState {
    private List<String> orderedActorIds = List.of();
    private int cursor = -1;

    public List<String> orderedActorIds() {
        return orderedActorIds;
    }

    public int cursor() {
        return cursor;
    }

    public int actorIndex(String actorId) {
        if (actorId == null || actorId.isBlank()) return -1;
        return orderedActorIds.indexOf(actorId);
    }

    public boolean cursorPassed(String actorId) {
        int actorIndex = actorIndex(actorId);
        return cursor >= 0 && actorIndex >= 0 && cursor > actorIndex;
    }

    /** Runtime/lifecycle mutation boundary. */
    void replaceOrderFromLifecycle(List<String> actorIds) {
        ArrayList<String> copy = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String actorId : actorIds == null ? List.<String>of() : actorIds) {
            if (actorId == null || actorId.isBlank()) {
                throw new IllegalArgumentException("initiative actor id is required");
            }
            String normalized = actorId.strip();
            if (!seen.add(normalized)) {
                throw new IllegalArgumentException("duplicate initiative actor id: " + normalized);
            }
            copy.add(normalized);
        }
        orderedActorIds = List.copyOf(copy);
        cursor = -1;
    }

    /** Runtime/lifecycle mutation boundary. Cursor -1 means initiative has not started. */
    void setCursorFromLifecycle(int cursor) {
        if (cursor < -1) {
            throw new IllegalArgumentException("initiative cursor cannot be less than -1");
        }
        this.cursor = cursor;
    }

    void resetCursorFromLifecycle() {
        cursor = -1;
    }
}
