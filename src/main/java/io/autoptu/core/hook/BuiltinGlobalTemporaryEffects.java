package io.autoptu.core.hook;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.RuntimeTickDamage;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;
import java.util.Map;

/** Built-in global temporary-effect rules frozen from the pinned Python oracle. */
public final class BuiltinGlobalTemporaryEffects {
    private BuiltinGlobalTemporaryEffects() {}

    public static GlobalTemporaryEffectPhaseRegistry registry() {
        return GlobalTemporaryEffectPhaseRegistry.builder()
                .register(
                        "ability.corrosive-toxins.end",
                        "corrosive_tick",
                        TurnPhase.END,
                        100,
                        BuiltinGlobalTemporaryEffects::corrosiveToxinsEnd
                )
                .build();
    }

    private static LifecycleHookResult corrosiveToxinsEnd(
            LifecycleHookContext context,
            String targetId,
            TemporaryEffectEntry entry
    ) {
        RuntimeCombatantState target = context.state().requireCombatant(targetId);
        int entryRound = intValue(entry.payload().get("round"));

        if (entryRound != context.round()) {
            target.temporaryEffects().removeFirst("corrosive_tick");
            return LifecycleHookResult.empty();
        }
        if (!context.state().hasStatus(targetId, "Poisoned")
                && !context.state().hasStatus(targetId, "Badly Poisoned")) {
            target.temporaryEffects().removeFirst("corrosive_tick");
            return LifecycleHookResult.empty();
        }

        int damage = RuntimeTickDamage.apply(context.state(), targetId, 1);
        target.temporaryEffects().removeFirst("corrosive_tick");
        return LifecycleHookResult.events(List.of(new AbilityEvent(
                context.actorId(),
                "Corrosive Toxins",
                "tick",
                Map.of(
                        "target", targetId,
                        "phase", context.phase().value(),
                        "amount", damage,
                        "description", "Corrosive Toxins applies a poison tick.",
                        "targetHp", target.hp()
                )
        )));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
