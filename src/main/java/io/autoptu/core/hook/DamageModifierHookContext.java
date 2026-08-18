package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.Set;

/**
 * Authoritative inputs exposed to damage-modifier hooks.
 *
 * The context contains server-owned battle state only. Minecraft adapters must
 * not inject trusted damage modifiers through this boundary.
 */
public record DamageModifierHookContext(
        BattleRuntimeState state,
        String actorId,
        String targetId,
        RuntimeCombatantState actor,
        RuntimeCombatantState target,
        MoveOption move,
        MoveCombatProfile metadata
) {
    public DamageModifierHookContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (actor == null) throw new IllegalArgumentException("actor is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (metadata == null) throw new IllegalArgumentException("metadata is required");
        if (!actorId.equals(actor.combatantId())) {
            throw new IllegalArgumentException("actorId must match actor state");
        }
        if (!targetId.equals(target.combatantId())) {
            throw new IllegalArgumentException("targetId must match target state");
        }
    }

    public Set<String> actorStatuses() {
        return state.statuses(actorId);
    }

    public Set<String> targetStatuses() {
        return state.statuses(targetId);
    }
}
