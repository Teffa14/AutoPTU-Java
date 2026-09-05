package io.autoptu.core.runtime;

/**
 * Server-authoritative PTU tick HP-loss boundary.
 *
 * <p>The pinned Python oracle routes poison-style effects through
 * {@code PokemonState._apply_tick_damage(ticks)} and returns the actual HP lost. A PTU tick is
 * one tenth of maximum HP, rounded down with a minimum value of one. This operation is HP loss,
 * not an ordinary move hit: it does not run Defense, type, action-economy, move-frequency, or
 * move-hit history logic.</p>
 *
 * <p>Rule hooks may request this operation through the core, but Minecraft/Cobblemon state must
 * never become the owner of the mutation.</p>
 */
public final class RuntimeTickDamage {
    private RuntimeTickDamage() {}

    /** Returns the canonical value of one PTU tick for the target. */
    public static int tickValue(BattleRuntimeState state, String targetId) {
        RuntimeCombatantState target = requireTarget(state, targetId);
        return Math.max(1, target.maxHp() / 10);
    }

    /**
     * Applies {@code ticks} canonical ticks directly to authoritative HP and returns actual HP lost.
     * Zero ticks is a no-op. Negative counts are rejected so hook data cannot heal accidentally.
     */
    public static int apply(BattleRuntimeState state, String targetId, int ticks) {
        RuntimeCombatantState target = requireTarget(state, targetId);
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks cannot be negative");
        }
        if (ticks == 0 || target.hp() <= 0) {
            return 0;
        }

        int requested = Math.multiplyExact(Math.max(1, target.maxHp() / 10), ticks);
        int previousHp = target.hp();
        target.setHp(previousHp - requested);
        return previousHp - target.hp();
    }

    private static RuntimeCombatantState requireTarget(BattleRuntimeState state, String targetId) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId is required");
        }
        return state.requireCombatant(targetId);
    }
}
