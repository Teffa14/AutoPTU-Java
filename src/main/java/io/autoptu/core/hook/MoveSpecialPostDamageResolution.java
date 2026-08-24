package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.runtime.BattleRuntimeState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime-facing bridge for Python move-special POST_DAMAGE dispatch.
 *
 * <p>POST_DAMAGE runs after the ordinary hit has already mutated HP/history in the pinned
 * Python engine. This bridge therefore preserves the shared mutable result and exposes the
 * already-applied {@code damage_dealt} to handlers without treating later result mutations as
 * retroactive damage authority.</p>
 */
public final class MoveSpecialPostDamageResolution {
    private MoveSpecialPostDamageResolution() {}

    public static Result resolve(
            MoveSpecialHookRegistry registry,
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String moveCategory,
            Map<String, ?> resultSnapshot,
            boolean hit,
            int damageDealt
    ) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(state, "state");

        LinkedHashMap<String, Object> initial = new LinkedHashMap<>();
        if (resultSnapshot != null) resultSnapshot.forEach(initial::put);
        initial.putIfAbsent("hit", hit);
        MoveSpecialResultState mutable = new MoveSpecialResultState(initial);

        List<BattleEvent> events = registry.dispatch(new MoveSpecialHookContext(
                state,
                attackerId,
                defenderId,
                moveName,
                moveCategory,
                mutable,
                hit,
                damageDealt,
                MoveSpecialPhase.POST_DAMAGE
        ));

        return new Result(events, mutable.snapshot());
    }

    public record Result(List<BattleEvent> events, Map<String, Object> resultSnapshot) {
        public Result {
            events = List.copyOf(events == null ? List.of() : events);
            resultSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(
                    resultSnapshot == null ? Map.of() : resultSnapshot));
        }
    }
}
