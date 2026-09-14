package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuntimeBattleEventSequencerTest {
    @Test
    void assignsDistinctMonotonicOccurrenceIdentityToRepeatedSemanticEvents() {
        RuntimeBattleEventSequencer sequencer = new RuntimeBattleEventSequencer();
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        BattleEventOccurrence first = sequencer.record(event);
        BattleEventOccurrence second = sequencer.record(event);

        assertEquals(event.stableKey(), first.event().stableKey());
        assertEquals(event.stableKey(), second.event().stableKey());
        assertEquals(1L, first.sequence());
        assertEquals(2L, second.sequence());
        assertNotEquals(first.occurrenceKey(), second.occurrenceKey());
        assertEquals(3L, sequencer.nextSequence());
    }
}
