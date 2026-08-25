package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.runtime.BattleRuntimeState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime-facing bridge for Python move-special END_ACTION dispatch. */
public final class MoveSpecialEndActionResolution {
    private MoveSpecialEndActionResolution() {}

    public static Result resolve(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String moveName,
            String moveCategory,
            Map<String, ?> lastResultSnapshot,
            int totalDamageDealt
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");

        LinkedHashMap<String, Object> initial = new LinkedHashMap<>();
        if (lastResultSnapshot != null) lastResultSnapshot.forEach(initial::put);
        MoveSpecialResultState mutable = new MoveSpecialResultState(initial);
        boolean hitSnapshot = mutable.hit();

        List<BattleEvent> events = registry.dispatch(new MoveSpecialHookContext(
                state,
                attackerId,
                "",
                moveName,
                moveCategory,
                mutable,
                hitSnapshot,
                Math.max(0, totalDamageDealt),
                MoveSpecialPhase.END_ACTION
        ));

        return new Result(events, mutable.snapshot(), hitSnapshot, Math.max(0, totalDamageDealt));
    }

    public record Result(
            List<BattleEvent> events,
            Map<String, Object> resultSnapshot,
            boolean hitSnapshot,
            int totalDamageDealt
    ) {
        public Result {
            events = List.copyOf(events == null ? List.of() : events);
            resultSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(
                    resultSnapshot == null ? Map.of() : resultSnapshot));
            totalDamageDealt = Math.max(0, totalDamageDealt);
        }
    }
}
