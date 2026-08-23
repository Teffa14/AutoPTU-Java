package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.AppliedActionResult;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.ReactionMovementApplication;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;

/** Built-in PRE-damage reactions backed by canonical battle state. */
public final class BuiltinPreDamageReactionHooks {
    private BuiltinPreDamageReactionHooks() {
    }

    public static PreDamageReactionHookRegistry registry() {
        return PreDamageReactionHookRegistry.builder()
                .register("telepathy-area-escape", HookSource.ABILITY, 100,
                        BuiltinPreDamageReactionHooks::telepathy)
                .build();
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
}
