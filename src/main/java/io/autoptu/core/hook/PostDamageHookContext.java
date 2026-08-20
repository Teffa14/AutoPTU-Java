package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

/** Authoritative state exposed to post-damage hooks after PTU damage arithmetic. */
public record PostDamageHookContext(
        BattleRuntimeState state,
        String actorId,
        String targetId,
        RuntimeCombatantState actor,
        RuntimeCombatantState target,
        MoveOption move,
        MoveCombatProfile metadata,
        DamageResult damage
) {
    public PostDamageHookContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (actor == null) throw new IllegalArgumentException("actor is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (metadata == null) throw new IllegalArgumentException("metadata is required");
        if (damage == null) throw new IllegalArgumentException("damage is required");
        if (!actorId.equals(actor.combatantId())) throw new IllegalArgumentException("actorId must match actor state");
        if (!targetId.equals(target.combatantId())) throw new IllegalArgumentException("targetId must match target state");
    }

    public PostDamageHookContext withDamage(DamageResult nextDamage) {
        return new PostDamageHookContext(state, actorId, targetId, actor, target, move, metadata, nextDamage);
    }
}
