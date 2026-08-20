package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

/** Server-owned inputs for effects applied after ordinary damage/type arithmetic. */
public record PostDamageHookContext(
        BattleRuntimeState state,
        String actorId,
        String targetId,
        RuntimeCombatantState actor,
        RuntimeCombatantState target,
        MoveOption move,
        MoveCombatProfile metadata,
        PythonRandom rng
) {
    public PostDamageHookContext {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        if (actor == null || !actorId.equals(actor.combatantId())) throw new IllegalArgumentException("actor state mismatch");
        if (target == null || !targetId.equals(target.combatantId())) throw new IllegalArgumentException("target state mismatch");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (metadata == null) throw new IllegalArgumentException("metadata is required");
    }

    /** Compatibility constructor for deterministic hooks that do not consume RNG. */
    public PostDamageHookContext(
            BattleRuntimeState state,
            String actorId,
            String targetId,
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            MoveOption move,
            MoveCombatProfile metadata
    ) {
        this(state, actorId, targetId, actor, target, move, metadata, null);
    }

    public PythonRandom requireRng() {
        if (rng == null) throw new IllegalStateException("post-damage RNG is not bound to this context");
        return rng;
    }
}
