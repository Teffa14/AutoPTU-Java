package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.CombatStageMutationResult;
import io.autoptu.core.runtime.CombatStageMutationService;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.StatusEntry;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Python-parity Shell Shield PRE-damage reaction.
 *
 * Readiness, statuses and combat stages are owned by BattleRuntimeState. The caller
 * can decide whether an optional out-of-turn reaction is accepted, but cannot supply
 * the readiness state, Withdrawn status or DEF stage mutation.
 */
public final class ShellShieldPreDamageReaction {
    private ShellShieldPreDamageReaction() {
    }

    public static PreDamageReactionResult apply(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Shell Shield")) return current;

        TemporaryEffectEntry ready = defender.temporaryEffects().getAll("shell_shield_ready").stream()
                .findFirst()
                .orElse(null);
        if (ready == null) return current;
        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Shell Shield", true))) {
            return current;
        }

        defender.temporaryEffects().removeFirst("shell_shield_ready");
        String abilityName = abilityName(ready);
        if (!state.hasStatus(context.defenderId(), "Withdrawn")) {
            state.putStatus(context.defenderId(), new StatusEntry("Withdrawn"));
        }

        CombatStageMutationResult stage = CombatStageMutationService.authoritative(state).apply(
                context.defenderId(),
                context.defenderId(),
                context.moveName(),
                CombatStat.DEF,
                1,
                "shell_shield"
        );

        ArrayList<BattleEvent> events = new ArrayList<>(current.events());
        events.addAll(stage.events());
        events.add(new RuleEffectEvent(
                "ability",
                abilityName,
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "withdraw",
                0.0,
                defender.hp()
        ));
        return new PreDamageReactionResult(
                current.hit(),
                current.damage(),
                current.typeMultiplier(),
                List.copyOf(events)
        );
    }

    private static String abilityName(TemporaryEffectEntry ready) {
        Object value = ready.payload().get("ability");
        if (value == null) return "Shell Shield";
        String name = String.valueOf(value).strip();
        return name.isEmpty() ? "Shell Shield" : name;
    }
}
