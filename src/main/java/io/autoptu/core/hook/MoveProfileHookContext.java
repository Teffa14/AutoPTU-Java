package io.autoptu.core.hook;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.runtime.AbilityState;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Objects;

/** Server-owned context for hooks that transform effective move metadata before damage resolution. */
public record MoveProfileHookContext(
        BattleRuntimeState state,
        String actorId,
        String targetId,
        RuntimeCombatantState actor,
        RuntimeCombatantState target,
        MoveOption move,
        MoveCombatProfile profile
) {
    public MoveProfileHookContext {
        state = Objects.requireNonNull(state, "state");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("targetId is required");
        actor = Objects.requireNonNull(actor, "actor");
        target = Objects.requireNonNull(target, "target");
        move = Objects.requireNonNull(move, "move");
        profile = Objects.requireNonNull(profile, "profile");
    }

    public List<AbilityState> actorAbilities() {
        return state.abilities(actorId);
    }

    public List<AbilityState> targetAbilities() {
        return state.abilities(targetId);
    }

    public MoveProfileHookContext withProfile(MoveCombatProfile nextProfile) {
        return new MoveProfileHookContext(state, actorId, targetId, actor, target, move, nextProfile);
    }
}
