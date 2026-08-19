package io.autoptu.core.hook;

import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;
import io.autoptu.core.runtime.TrainerRuntimeState;

import java.util.List;
import java.util.Map;

/** Built-in Trainer Feature/perk phase rules frozen from the pinned Python oracle. */
public final class BuiltinPerkPhaseEffects {
    private BuiltinPerkPhaseEffects() {}

    public static PerkPhaseEffectRegistry registry() {
        return PerkPhaseEffectRegistry.builder()
                .register("perk.attack-link.end", "Attack Link", TurnPhase.END, 10,
                        (context, featureName) -> applyLink(context, featureName, CombatStat.ATK, "atk"))
                .register("perk.defense-link.end", "Defense Link", TurnPhase.END, 20,
                        (context, featureName) -> applyLink(context, featureName, CombatStat.DEF, "def"))
                .register("perk.special-attack-link.end", "Special Attack Link", TurnPhase.END, 30,
                        (context, featureName) -> applyLink(context, featureName, CombatStat.SPATK, "spatk"))
                .register("perk.special-defense-link.end", "Special Defense Link", TurnPhase.END, 40,
                        (context, featureName) -> applyLink(context, featureName, CombatStat.SPDEF, "spdef"))
                .register("perk.speed-link.end", "Speed Link", TurnPhase.END, 50,
                        (context, featureName) -> applyLink(context, featureName, CombatStat.SPD, "spd"))
                .register(
                        "perk.defense-mastery.end",
                        "Defense Mastery",
                        TurnPhase.END,
                        100,
                        BuiltinPerkPhaseEffects::defenseMasteryEnd
                )
                .build();
    }

    private static LifecycleHookResult applyLink(
            LifecycleHookContext context,
            String featureName,
            CombatStat stat,
            String pythonStat
    ) {
        RuntimeCombatantState actor = context.state().requireCombatant(context.actorId());
        if (actor.hp() <= 0) return LifecycleHookResult.empty();

        TrainerRuntimeState trainer = context.state().requireTrainerForCombatant(context.actorId());
        if (trainer.ap() < 1) return LifecycleHookResult.empty();

        int current = actor.combatStages().get(stat);
        if (current > 0) return LifecycleHookResult.empty();

        int next = actor.combatStages().adjust(stat, 1);
        if (!trainer.spendAp(1)) {
            actor.combatStages().set(stat, current);
            return LifecycleHookResult.empty();
        }

        TrainerFeatureEvent event = new TrainerFeatureEvent(
                context.actorId(),
                featureName,
                "raise_cs",
                Map.of(
                        "trainer", trainer.trainerId(),
                        "stat", pythonStat,
                        "amount", next - current,
                        "ap_spent", 1,
                        "phase", "end",
                        "description", featureName + " spends 1 AP to raise the Pokemon's combat stage.",
                        "targetHp", actor.hp()
                )
        );
        return LifecycleHookResult.events(List.of(event));
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
