package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.List;

/** Result of advancing the canonical initiative cursor within the current round. */
public record InitiativeTurnAdvanceResult(
        String actorId,
        int initiativeIndex,
        boolean roundExhausted,
        List<BattleEvent> events
) {
    public InitiativeTurnAdvanceResult {
        actorId = actorId == null ? "" : actorId.strip();
        if (initiativeIndex < -1) throw new IllegalArgumentException("initiativeIndex cannot be less than -1");
        if (roundExhausted && !actorId.isBlank()) {
            throw new IllegalArgumentException("an exhausted round cannot have an active actor");
        }
        events = List.copyOf(new ArrayList<>(events == null ? List.of() : events));
    }

    public boolean hasActor() {
        return !actorId.isBlank();
    }

    public static InitiativeTurnAdvanceResult actor(String actorId, int initiativeIndex, List<BattleEvent> events) {
        return new InitiativeTurnAdvanceResult(actorId, initiativeIndex, false, events);
    }

    public static InitiativeTurnAdvanceResult exhausted(int initiativeIndex) {
        return new InitiativeTurnAdvanceResult("", initiativeIndex, true, List.of());
    }
}
