package io.autoptu.core.runtime;

import io.autoptu.core.random.PythonRandom;

import java.util.List;

/**
 * Server-owned delayed-hit queue and battle RNG stream used by round lifecycle execution.
 *
 * Minecraft/Cobblemon adapters may observe resulting events, but they do not own the queue
 * or receive a mutable RNG handle. Scheduling/execution mutations stay inside the runtime package.
 */
public final class BattleDelayedHitState {
    private final DelayedHitQueue queue = new DelayedHitQueue();
    private final BattleRandomState randomState;

    public BattleDelayedHitState(long seed) {
        this.randomState = new BattleRandomState(seed);
    }

    /** Read-only snapshot suitable for persistence/debugging without exposing queue mutation. */
    public List<DelayedHitEntry> entriesInInsertionOrder() {
        return queue.entriesInInsertionOrder();
    }

    public int size() {
        return queue.size();
    }

    void scheduleFromRuntime(DelayedHitEntry entry) {
        queue.schedule(entry);
    }

    DelayedHitBatch takeDueFromLifecycle(int currentRound) {
        return queue.takeDue(currentRound);
    }

    PythonRandom randomFromRuntime() {
        return randomState.random();
    }
}
