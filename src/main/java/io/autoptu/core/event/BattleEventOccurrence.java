package io.autoptu.core.event;

import java.util.Objects;

/**
 * One battle-local occurrence of an authoritative semantic event.
 *
 * <p>The sequence is monotonic for the owning battle runtime. It distinguishes repeated events
 * that intentionally have the same semantic payload and therefore the same {@link BattleEvent#stableKey()}.</p>
 */
public record BattleEventOccurrence(long sequence, BattleEvent event) {
    public BattleEventOccurrence {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        event = Objects.requireNonNull(event, "event");
    }

    public String occurrenceKey() {
        return "event-sequence=" + sequence + "|event=" + event.stableKey();
    }
}
