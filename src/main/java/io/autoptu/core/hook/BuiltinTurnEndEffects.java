package io.autoptu.core.hook;

import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.Locale;

/** Built-in TURN_END rules frozen from the pinned Python oracle. */
public final class BuiltinTurnEndEffects {
    private static final String ADAPTIVE_GEOGRAPHY = "Adaptive Geography";
    private static final String TERRAIN_ALIAS = "terrain_alias";

    private BuiltinTurnEndEffects() {}

    public static TurnEndEffectRegistry registry() {
        return TurnEndEffectRegistry.builder()
                .register(
                        "adaptive-geography-terrain-alias-cleanup",
                        TurnEndEffectRegistry.Scope.ACTOR,
                        10,
                        BuiltinTurnEndEffects::clearAdaptiveGeography
                )
                .build();
    }

    private static LifecycleHookResult clearAdaptiveGeography(
            LifecycleHookContext context,
            String combatantId
    ) {
        RuntimeCombatantState actor = context.state().requireCombatant(combatantId);
        actor.temporaryEffects().removeIf(TERRAIN_ALIAS, BuiltinTurnEndEffects::isAdaptiveGeographyAlias);
        return LifecycleHookResult.empty();
    }

    private static boolean isAdaptiveGeographyAlias(TemporaryEffectEntry entry) {
        Object feature = entry.payload().get("feature");
        return feature instanceof String value
                && value.strip().toLowerCase(Locale.ROOT).equals(ADAPTIVE_GEOGRAPHY.toLowerCase(Locale.ROOT));
    }
}
