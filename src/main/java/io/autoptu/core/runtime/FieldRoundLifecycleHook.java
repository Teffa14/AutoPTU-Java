package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;

/** Applies Python-compatible terrain -> zones -> rooms progression to authoritative battle state. */
public final class FieldRoundLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context.point() != LifecycleHookPoint.ROUND_START) {
            throw new IllegalArgumentException("field round progression only runs at ROUND_START");
        }

        BattleRuntimeState state = context.state();
        BattleEnvironmentState environment = state.environment();
        FieldRoundProgressionResult result = FieldRoundProgression.advance(
                context.round(),
                environment.terrainEffect().orElse(null),
                environment.zoneEffects(),
                environment.roomEffects()
        );

        BattleEnvironmentState next = environment.withFieldEffects(
                result.terrain().orElse(null),
                result.zones(),
                result.rooms()
        );
        state.syncEnvironmentFromRuntime(next);

        for (FieldStatusCleanupRequest cleanup : result.statusCleanups()) {
            for (String combatantId : state.combatantIds()) {
                for (String status : cleanup.statusNames()) {
                    state.removeStatus(combatantId, status);
                }
            }
        }

        return LifecycleHookResult.events(result.events());
    }
}
