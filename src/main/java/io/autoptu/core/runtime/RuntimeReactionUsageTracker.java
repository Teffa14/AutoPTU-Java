package io.autoptu.core.runtime;

import java.util.Map;
import java.util.Objects;

/**
 * Runtime-owned reaction usage facade bound to the canonical battle round.
 *
 * <p>The underlying store remains generic by combatant and reaction key. This facade
 * prevents callers from supplying a second round clock: every query and committed use
 * reads {@link BattleRuntimeState#currentRound()} from the authoritative battle state.
 * Minecraft/Cobblemon adapters may inspect usage through this type, but only runtime
 * package code can commit uses or perform lifecycle pruning.</p>
 */
public final class RuntimeReactionUsageTracker {
    private final BattleRuntimeState battleState;
    private final ReactionUsageState usageState;

    public RuntimeReactionUsageTracker(BattleRuntimeState battleState) {
        this(battleState, new ReactionUsageState());
    }

    RuntimeReactionUsageTracker(BattleRuntimeState battleState, ReactionUsageState usageState) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
        this.usageState = Objects.requireNonNull(usageState, "usageState");
    }

    /** Number of committed uses for this combatant/reaction in the authoritative current round. */
    public int usesThisRound(String combatantId, String reactionKey) {
        return usageState.usesThisRound(combatantId, reactionKey, battleState.currentRound());
    }

    /** Read-only deterministic snapshot for the authoritative current round. */
    public Map<String, Integer> snapshotForCurrentRound() {
        return usageState.snapshotForRound(battleState.currentRound());
    }

    /** Runtime-only commit boundary; execution code calls this only after a reaction is committed. */
    void recordUseFromRuntime(String combatantId, String reactionKey) {
        usageState.recordUseFromRuntime(combatantId, reactionKey, battleState.currentRound());
    }

    /** Lifecycle-only retention boundary. Current/future entries survive; past rounds are discarded. */
    void pruneForCurrentRoundFromLifecycle() {
        usageState.pruneForRoundFromLifecycle(battleState.currentRound());
    }
}
