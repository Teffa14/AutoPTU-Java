package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered server-owned delayed-hit state.
 *
 * Mirrors Python's delayed_hits list: scheduled entries preserve insertion order;
 * entries whose trigger round is due are removed together, while future entries stay
 * queued. Actual move execution is intentionally a separate lifecycle slice.
 */
public final class DelayedHitQueue {
    private final ArrayList<DelayedHitEntry> entries = new ArrayList<>();

    public void schedule(DelayedHitEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry is required");
        }
        entries.add(entry);
    }

    public void schedule(
            String attackerId,
            String moveId,
            String targetId,
            io.autoptu.core.model.GridCoord targetPosition,
            int triggerRound,
            String effect
    ) {
        schedule(new DelayedHitEntry(attackerId, moveId, targetId, targetPosition, triggerRound, effect));
    }

    public List<DelayedHitEntry> entriesInInsertionOrder() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    /**
     * Remove every entry whose trigger round is less than or equal to currentRound.
     * Due and remaining entries both preserve their original insertion order.
     */
    public DelayedHitBatch takeDue(int currentRound) {
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound cannot be negative");
        }
        ArrayList<DelayedHitEntry> due = new ArrayList<>();
        ArrayList<DelayedHitEntry> remaining = new ArrayList<>();
        for (DelayedHitEntry entry : entries) {
            if (entry.triggerRound() <= currentRound) {
                due.add(entry);
            } else {
                remaining.add(entry);
            }
        }
        entries.clear();
        entries.addAll(remaining);
        return new DelayedHitBatch(due, remaining);
    }
}
