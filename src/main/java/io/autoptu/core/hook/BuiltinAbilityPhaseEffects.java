package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;
import java.util.Map;

/** Built-in phase ability rules frozen from the pinned Python oracle. */
public final class BuiltinAbilityPhaseEffects {
    private BuiltinAbilityPhaseEffects() {}

    public static AbilityPhaseEffectRegistry lancerRegistry() {
        return AbilityPhaseEffectRegistry.builder()
                .register("ability.lancer.end", "Lancer", TurnPhase.END, 100, BuiltinAbilityPhaseEffects::lancerEnd)
                .build();
    }

    private static LifecycleHookResult lancerEnd(LifecycleHookContext context, String registeredAbility) {
        RuntimeCombatantState actor = context.state().requireCombatant(context.actorId());
        int shiftedDistance = 0;
        for (TemporaryEffectEntry entry : actor.temporaryEffects().getAll("lancer_shift")) {
            int entryRound = intValue(entry.payload().get("round"));
            if (entryRound == context.round()) {
                shiftedDistance = Math.max(shiftedDistance, intValue(entry.payload().get("distance")));
            }
        }
        actor.temporaryEffects().removeIf(
                "lancer_shift",
                entry -> intValue(entry.payload().get("round")) != context.round()
        );

        if (shiftedDistance >= 3) {
            actor.temporaryEffects().add("crit_range_bonus", Map.of(
                    "bonus", 3,
                    "source", "Lancer",
                    "expires_round", context.round() + 1
            ));
            return LifecycleHookResult.events(List.of(new RuleEffectEvent(
                    "ability", "Lancer", context.actorId(), "", "", "crit_range", 3.0, actor.hp()
            )));
        }

        if (actor.actionBudget().hasActionAvailable(ActionType.SHIFT)) {
            actor.temporaryEffects().add("damage_reduction", Map.of(
                    "amount", 5,
                    "round", context.round(),
                    "expires_round", context.round() + 1,
                    "source", "Lancer",
                    "consume", false
            ));
            return LifecycleHookResult.events(List.of(new RuleEffectEvent(
                    "ability", "Lancer", context.actorId(), "", "", "damage_reduction", 5.0, actor.hp()
            )));
        }
        return LifecycleHookResult.empty();
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
