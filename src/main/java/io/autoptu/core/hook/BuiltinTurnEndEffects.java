package io.autoptu.core.hook;

import java.util.List;
import java.util.Map;

/** Built-in TURN_END rules frozen from the pinned Python oracle. */
public final class BuiltinTurnEndEffects {
    private static final SelectiveTemporaryEffectCleanupLifecycleHook ADAPTIVE_GEOGRAPHY_CLEANUP =
            new SelectiveTemporaryEffectCleanupLifecycleHook(
                    SelectiveTemporaryEffectCleanupLifecycleHook.Scope.ACTOR,
                    List.of(new SelectiveTemporaryEffectCleanupLifecycleHook.Selector(
                            "terrain_alias",
                            Map.of("feature", "Adaptive Geography")
                    ))
            );

    private BuiltinTurnEndEffects() {}

    public static TurnEndEffectRegistry registry() {
        return TurnEndEffectRegistry.builder()
                .register(
                        "adaptive-geography-terrain-alias-cleanup",
                        TurnEndEffectRegistry.Scope.ACTOR,
                        10,
                        (context, combatantId) -> ADAPTIVE_GEOGRAPHY_CLEANUP.apply(context)
                )
                .build();
    }
}
