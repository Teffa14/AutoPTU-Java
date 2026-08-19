package io.autoptu.core.hook;

import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;
import java.util.Map;

/** Built-in Trainer Feature/perk phase rules frozen from the pinned Python oracle. */
public final class BuiltinPerkPhaseEffects {
    private BuiltinPerkPhaseEffects() {}

    public static PerkPhaseEffectRegistry registry() {
        return PerkPhaseEffectRegistry.builder()
                .register(
                        "perk.defense-mastery.end",
                        "Defense Mastery",
                        TurnPhase.END,
                        100,
                        BuiltinPerkPhaseEffects::defenseMasteryEnd
                )
                .build();
    }

    private static LifecycleHookResult defenseMasteryEnd(LifecycleHookContext context, String featureName) {
        RuntimeCombatantState actor = context.state().requireCombatant(context.actorId());
        if (actor.hp() <= 0) return LifecycleHookResult.empty();

        boolean shiftedThisTurn = false;
        for (TemporaryEffectEntry entry : actor.temporaryEffects().getAll("shifted_this_turn")) {
            if (intValue(entry.payload().get("round"), -1) == context.round()) {
                shiftedThisTurn = true;
                break;
            }
        }
        if (shiftedThisTurn) return LifecycleHookResult.empty();

        actor.temporaryEffects().add("damage_reduction", Map.of(
                "amount", 5,
                "expires_round", context.round() + 1,
                "consume", false,
                "source", featureName
        ));
        String trainerId = context.state().controllerId(context.actorId());
        TrainerFeatureEvent event = new TrainerFeatureEvent(
                context.actorId(),
                featureName,
                "damage_reduction",
                Map.of(
                        "trainer", trainerId,
                        "amount", 5,
                        "phase", "end",
                        "description", featureName + " grants 5 damage reduction after holding position.",
                        "targetHp", actor.hp()
                )
        );
        return LifecycleHookResult.events(List.of(event));
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
