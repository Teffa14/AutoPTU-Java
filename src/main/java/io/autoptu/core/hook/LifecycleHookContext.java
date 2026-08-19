package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RoundDamageHistoryState;

import java.util.Objects;

/** Server-owned context exposed to lifecycle hooks. */
public record LifecycleHookContext(
        BattleRuntimeState state,
        RoundDamageHistoryState damageHistory,
        LifecycleHookPoint point,
        int previousRound,
        int round,
        String actorId
) {
    public LifecycleHookContext {
        state = Objects.requireNonNull(state, "state");
        damageHistory = Objects.requireNonNull(damageHistory, "damageHistory");
        point = Objects.requireNonNull(point, "point");
        if (previousRound < 0) throw new IllegalArgumentException("previousRound cannot be negative");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        actorId = actorId == null ? "" : actorId.strip();
    }

    /** Compatibility constructor for existing hook tests/callers without seeded history. */
    public LifecycleHookContext(
            BattleRuntimeState state,
            LifecycleHookPoint point,
            int previousRound,
            int round,
            String actorId
    ) {
        this(state, new RoundDamageHistoryState(), point, previousRound, round, actorId);
    }
}
