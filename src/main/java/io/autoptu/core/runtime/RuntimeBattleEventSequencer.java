package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.BattleEventOccurrence;

import java.util.Objects;

/**
 * Battle-local monotonic sequence for authoritative semantic events.
 *
 * <p>The sequencer owns identity only. It does not emit, execute, deduplicate, or reorder events.</p>
 */
public final class RuntimeBattleEventSequencer {
    private long nextSequence = 1L;

    public BattleEventOccurrence record(BattleEvent event) {
        Objects.requireNonNull(event, "event");
        if (nextSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("battle event sequence exhausted");
        }
        return new BattleEventOccurrence(nextSequence++, event);
    }

    public long nextSequence() {
        return nextSequence;
    }
}
