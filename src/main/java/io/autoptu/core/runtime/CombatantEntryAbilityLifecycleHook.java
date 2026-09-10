package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;

import java.util.List;

/**
 * Authoritative field-entry seam for ability families that trigger when a combatant becomes active.
 *
 * <p>The hook owns entry-scoped state only. Ability mutation remains in reusable family executors.
 * Adapters may request a switch, but they never run these PTU effects themselves.</p>
 */
public final class CombatantEntryAbilityLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context.point() != LifecycleHookPoint.COMBATANT_ENTRY) {
            throw new IllegalArgumentException("combatant entry hook requires COMBATANT_ENTRY context");
        }
        String actorId = context.actorId();
        if (actorId.isBlank()) {
            throw new IllegalArgumentException("combatant entry actor is required");
        }

        BattleRuntimeState state = context.state();
        RuntimeCombatantState actor = state.requireCombatant(actorId);
        if (!state.isActive(actorId) || actor.hp() <= 0) {
            return LifecycleHookResult.empty();
        }

        int round = state.currentRound();
        if (!hasRoundEffect(actor, ImpostorEffectExecutor.JOINED_ROUND, round)) {
            actor.temporaryEffects().add(ImpostorEffectExecutor.JOINED_ROUND, java.util.Map.of("round", round));
        }

        List<BattleEvent> events = ImpostorEffectExecutor.apply(state, actorId);
        return LifecycleHookResult.events(events);
    }

    private static boolean hasRoundEffect(RuntimeCombatantState combatant, String effectName, int round) {
        for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll(effectName)) {
            Object value = entry.payload().get("round");
            if (value instanceof Number number && number.intValue() == round) return true;
        }
        return false;
    }
}
