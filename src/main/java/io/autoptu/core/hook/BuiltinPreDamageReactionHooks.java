package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.AppliedActionResult;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.ReactionMovementApplication;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;

import java.util.List;
import java.util.Map;

/** Built-in PRE-damage reactions backed by canonical battle state. */
public final class BuiltinPreDamageReactionHooks {
    private BuiltinPreDamageReactionHooks() {
    }

    public static PreDamageReactionHookRegistry registry() {
        return PreDamageReactionHookRegistry.builder()
                .register("perception-area-escape", HookSource.ABILITY, 90,
                        BuiltinPreDamageReactionHooks::perception)
                .register("telepathy-area-escape", HookSource.ABILITY, 100,
                        BuiltinPreDamageReactionHooks::telepathy)
                .build();
    }

    /**
     * Python Perception parity for the reusable PRE-damage boundary.
     *
     * <p>Perception requires a server-owned {@code perception_ready} temporary effect.
     * The first optional decision happens before that readiness is consumed. Once accepted,
     * readiness is consumed even if later geometry/cooldown checks prevent movement. A
     * non-expired {@code perception_used} entry blocks the reaction; expired entries are
     * removed in Python snapshot order. If the second optional decision is accepted and a
     * safe tile exists, the defender shifts without spending its normal SHIFT, records
     * {@code perception_used(expires_round=currentRound+1)}, emits the ability event, and
     * cancels the incoming hit.</p>
     */
    private static PreDamageReactionResult perception(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Perception")) return current;
        if (!defender.temporaryEffects().has("perception_ready")) return current;

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Perception", true))) {
            return current;
        }
        defender.temporaryEffects().removeFirst("perception_ready");

        GridCoord origin = defender.position();
        if (context.threatenedTiles().isEmpty() || !context.threatenedTiles().contains(origin)) {
            return current;
        }

        for (TemporaryEffectEntry entry : defender.temporaryEffects().getAll("perception_used")) {
            Object expiresRound = entry.payload().get("expires_round");
            if (expiresRound != null && state.currentRound() > intLike(expiresRound)) {
                defender.temporaryEffects().removeFirst("perception_used");
                continue;
            }
            return current;
        }

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Perception [Errata]", true))) {
            return current;
        }

        AppliedActionResult movement = ReactionMovementApplication.escapeThreatenedArea(
                state,
                context.defenderId(),
                context.threatenedTiles(),
                null
        );
        if (movement.events().isEmpty() || defender.position().equals(origin)) {
            return current;
        }

        defender.temporaryEffects().add(
                "perception_used",
                Map.of("expires_round", state.currentRound() + 1)
        );
        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Perception",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "shift",
                0.0,
                defender.hp()
        );
        return current.cancelHit(List.of(event));
    }

    /**
     * Python Telepathy parity for the reusable PRE-damage boundary.
     *
     * <p>The Python ability registry suppresses defender-owned hooks when the defender's
     * abilities are suppressed or when the attacker has Mold Breaker. Telepathy then
     * requires an allied attacker, asks the optional out-of-turn decision gate, requires
     * the defender to be inside the threatened area, shifts to the farthest legal safe
     * tile, emits the ability event, and cancels hit/damage/type effectiveness.</p>
     */
    private static PreDamageReactionResult telepathy(
            PreDamageReactionContext context,
            PreDamageReactionResult current
    ) {
        BattleRuntimeState state = context.state();
        RuntimeCombatantState attacker = state.requireCombatant(context.attackerId());
        RuntimeCombatantState defender = state.requireCombatant(context.defenderId());

        if (AbilityIdentityResolution.matchesRegistration(attacker.abilities(), "Mold Breaker")) return current;
        if (defender.abilitiesSuppressed()) return current;
        if (!AbilityIdentityResolution.matchesRegistration(defender.abilities(), "Telepathy")) return current;
        if (!state.teamId(context.attackerId()).equals(state.teamId(context.defenderId()))) return current;

        if (!context.outOfTurnDecision().shouldTrigger(
                context.decisionRequest(context.defenderId(), "Telepathy", true))) {
            return current;
        }

        GridCoord origin = defender.position();
        if (context.threatenedTiles().isEmpty() || !context.threatenedTiles().contains(origin)) {
            return current;
        }

        AppliedActionResult movement = ReactionMovementApplication.escapeThreatenedArea(
                state,
                context.defenderId(),
                context.threatenedTiles(),
                null
        );
        if (movement.events().isEmpty() || defender.position().equals(origin)) {
            return current;
        }

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Telepathy",
                context.defenderId(),
                context.attackerId(),
                context.moveName(),
                "shift",
                0.0,
                defender.hp()
        );
        return current.cancelHit(List.of(event));
    }

    private static int intLike(Object value) {
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value).strip());
    }
}
