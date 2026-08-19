package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

/**
 * Read-only authoritative context for hooks that transform effective move metadata
 * before damage arithmetic. Hooks return a new profile; they must not mutate battle
 * state, consume resources, or trust adapter-supplied rule data.
 */
public record EffectiveMoveHookContext(
        BattleRuntimeState state,
        String actorId,
        String targetId,
        RuntimeCombatantState actor,
        RuntimeCombatantState target,
        MoveOption move,
        MoveCombatProfile baseProfile,
        MoveCombatProfile effectiveProfile
) {
    public EffectiveMoveHookContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (actor == null) throw new IllegalArgumentException("actor is required");
        if (target == null) throw new IllegalArgumentException("target is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (baseProfile == null) throw new IllegalArgumentException("baseProfile is required");
        if (effectiveProfile == null) throw new IllegalArgumentException("effectiveProfile is required");
        if (!actorId.equals(actor.combatantId())) throw new IllegalArgumentException("actorId must match actor state");
        if (!targetId.equals(target.combatantId())) throw new IllegalArgumentException("targetId must match target state");
    }

    public EffectiveMoveHookContext withEffectiveProfile(MoveCombatProfile profile) {
        return new EffectiveMoveHookContext(
                state, actorId, targetId, actor, target, move, baseProfile, profile
        );
    }
}
