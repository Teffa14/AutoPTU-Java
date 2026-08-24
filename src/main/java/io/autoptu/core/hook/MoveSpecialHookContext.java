package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.Locale;
import java.util.Objects;

/** Internal authoritative context for move-special dispatch. */
public record MoveSpecialHookContext(
        BattleRuntimeState state,
        String attackerId,
        String defenderId,
        String moveName,
        String moveCategory,
        MoveSpecialResultState result,
        boolean hit,
        MoveSpecialPhase phase
) {
    public MoveSpecialHookContext {
        state = Objects.requireNonNull(state, "state");
        if (attackerId == null || attackerId.isBlank()) throw new IllegalArgumentException("attackerId is required");
        state.requireCombatant(attackerId);
        defenderId = defenderId == null ? "" : defenderId.strip();
        if (!defenderId.isEmpty()) state.requireCombatant(defenderId);
        moveName = moveName == null ? "" : moveName.strip().toLowerCase(Locale.ROOT);
        moveCategory = moveCategory == null ? "" : moveCategory.strip().toLowerCase(Locale.ROOT);
        result = Objects.requireNonNull(result, "result");
        phase = phase == null ? MoveSpecialPhase.POST_DAMAGE : phase;
    }

    /** Compatibility constructor for hooks that only need the dispatch-start hit snapshot. */
    public MoveSpecialHookContext(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String moveCategory,
            boolean hit,
            MoveSpecialPhase phase
    ) {
        this(state, attackerId, defenderId, moveName, moveCategory,
                MoveSpecialResultState.withHit(hit), hit, phase);
    }

    /** Python-compatible constructor: hit is snapshotted before handlers mutate the shared result. */
    public MoveSpecialHookContext(
            BattleRuntimeState state,
            String attackerId,
            String defenderId,
            String moveName,
            String moveCategory,
            MoveSpecialResultState result,
            MoveSpecialPhase phase
    ) {
        this(state, attackerId, defenderId, moveName, moveCategory, result, result.hit(), phase);
    }

    public RuntimeCombatantState attacker() {
        return state.requireCombatant(attackerId);
    }

    public RuntimeCombatantState defender() {
        return defenderId.isEmpty() ? null : state.requireCombatant(defenderId);
    }

    public boolean shieldDustBlocksPostDamage() {
        RuntimeCombatantState defender = defender();
        return phase == MoveSpecialPhase.POST_DAMAGE
                && !moveCategory.equals("status")
                && defender != null
                && defender.hasAbilityExact("Shield Dust");
    }
}
