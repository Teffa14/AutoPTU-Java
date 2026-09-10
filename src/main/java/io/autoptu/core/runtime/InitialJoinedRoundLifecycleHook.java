package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookResult;

import java.util.Map;

/**
 * Materializes the pinned Python round-one joined_round state before round-start effects execute.
 *
 * <p>Python PhaseController.start_round() records joined_round=1 for every active combatant on the
 * initial round before Trainer Feature and ability triggers. Later entry/switch paths own their own
 * joined-round mutation; this hook intentionally handles initial setup only.</p>
 */
public final class InitialJoinedRoundLifecycleHook implements LifecycleHook {
    public static final String EFFECT = "joined_round";

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context.round() != 1) return LifecycleHookResult.empty();

        BattleRuntimeState state = context.state();
        for (String combatantId : state.combatantIds()) {
            RuntimeCombatantState combatant = state.requireCombatant(combatantId);
            if (!state.isActive(combatantId) || combatant.hp() <= 0) continue;
            if (hasRoundMarker(combatant, context.round())) continue;
            combatant.temporaryEffects().add(EFFECT, Map.of("round", context.round()));
        }
        return LifecycleHookResult.empty();
    }

    private static boolean hasRoundMarker(RuntimeCombatantState combatant, int round) {
        for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll(EFFECT)) {
            Object value = entry.payload().get("round");
            if (value instanceof Number number && number.intValue() == round) return true;
        }
        return false;
    }
}
