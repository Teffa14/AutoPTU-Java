package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntimeState;

import java.util.Objects;

/** Server-owned context exposed to lifecycle hooks. */
public record LifecycleHookContext(
        BattleRuntimeState state,
        LifecycleHookPoint point,
        int previousRound,
        int round,
        String actorId
) {
    public LifecycleHookContext {
        state = Objects.requireNonNull(state, "state");
        point = Objects.requireNonNull(point, "point");
        if (previousRound < 0) throw new IllegalArgumentException("previousRound cannot be negative");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        actorId = actorId == null ? "" : actorId.strip();
    }
}
