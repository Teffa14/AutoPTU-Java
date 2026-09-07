package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Server-owned registry for ability families dispatched by {@link RoundStartAbilityDispatchPlan}.
 *
 * <p>The registry may be populated incrementally while the Python port is incomplete. Missing
 * families remain explicit unhandled results in the executor rather than silently acquiring
 * guessed Java behavior.</p>
 */
public final class RoundStartAbilityEffectRegistry {
    @FunctionalInterface
    public interface Handler {
        List<BattleEvent> apply(RoundStartAbilityDispatchPlan.Invocation invocation, BattleRuntimeState state);
    }

    private final LinkedHashMap<String, Handler> handlers = new LinkedHashMap<>();

    public RoundStartAbilityEffectRegistry register(String family, Handler handler) {
        String key = normalizeFamily(family);
        if (key.isEmpty()) throw new IllegalArgumentException("family is required");
        handlers.put(key, Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public boolean supports(String family) {
        return handlers.containsKey(normalizeFamily(family));
    }

    public List<BattleEvent> apply(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state
    ) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(state, "state");
        Handler handler = handlers.get(normalizeFamily(invocation.family()));
        if (handler == null) return List.of();
        List<BattleEvent> events = handler.apply(invocation, state);
        if (events == null || events.isEmpty()) return List.of();
        ArrayList<BattleEvent> copied = new ArrayList<>();
        for (BattleEvent event : events) {
            if (event == null) throw new IllegalArgumentException("ability handler returned null event");
            copied.add(event);
        }
        return List.copyOf(copied);
    }

    /** First frozen Python family: Air Lock emits suppression events without mutating weather. */
    public static RoundStartAbilityEffectRegistry pythonParityBuiltins() {
        return new RoundStartAbilityEffectRegistry().register(
                RoundStartAbilityDispatchPlan.AIR_LOCK,
                RoundStartAbilityEffectRegistry::applyAirLock
        );
    }

    private static List<BattleEvent> applyAirLock(
            RoundStartAbilityDispatchPlan.Invocation invocation,
            BattleRuntimeState state
    ) {
        if (invocation.scope() != RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER) {
            throw new IllegalArgumentException("Air Lock requires ABILITY_HOLDER scope");
        }
        String weather = state.environment().weather();
        return List.of(new AbilityEvent(
                invocation.actorId(),
                "Air Lock",
                "weather_suppress",
                Map.of(
                        "weather", weather,
                        "description", "Air Lock suppresses the active weather."
                )
        ));
    }

    private static String normalizeFamily(String family) {
        return family == null ? "" : family.strip().toLowerCase(Locale.ROOT);
    }
}
