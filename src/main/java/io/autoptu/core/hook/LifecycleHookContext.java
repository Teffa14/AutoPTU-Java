package io.autoptu.core.hook;

import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RoundDamageHistoryState;
import io.autoptu.core.runtime.RoundInjuryHistoryState;

import java.util.Objects;

/** Server-owned context exposed to lifecycle hooks. */
public record LifecycleHookContext(
        BattleRuntimeState state,
        RoundDamageHistoryState damageHistory,
        RoundInjuryHistoryState injuryHistory,
        LifecycleHookPoint point,
        int previousRound,
        int round,
        String actorId,
        TurnPhase phase
) {
    public LifecycleHookContext {
        state = Objects.requireNonNull(state, "state");
        damageHistory = Objects.requireNonNull(damageHistory, "damageHistory");
        injuryHistory = Objects.requireNonNull(injuryHistory, "injuryHistory");
        point = Objects.requireNonNull(point, "point");
        if (previousRound < 0) throw new IllegalArgumentException("previousRound cannot be negative");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        actorId = actorId == null ? "" : actorId.strip();
        if (point == LifecycleHookPoint.PHASE_CHANGE && phase == null) {
            throw new IllegalArgumentException("phase is required for PHASE_CHANGE hooks");
        }
    }

    public LifecycleHookContext(
            BattleRuntimeState state,
            RoundDamageHistoryState damageHistory,
            RoundInjuryHistoryState injuryHistory,
            LifecycleHookPoint point,
            int previousRound,
            int round,
            String actorId
    ) {
        this(state, damageHistory, injuryHistory, point, previousRound, round, actorId, null);
    }

    public LifecycleHookContext(
            BattleRuntimeState state,
            RoundDamageHistoryState damageHistory,
            LifecycleHookPoint point,
            int previousRound,
            int round,
            String actorId
    ) {
        this(state, damageHistory, new RoundInjuryHistoryState(), point, previousRound, round, actorId, null);
    }

    public LifecycleHookContext(
            BattleRuntimeState state,
            LifecycleHookPoint point,
            int previousRound,
            int round,
            String actorId
    ) {
        this(state, new RoundDamageHistoryState(), new RoundInjuryHistoryState(), point, previousRound, round, actorId, null);
    }
}
