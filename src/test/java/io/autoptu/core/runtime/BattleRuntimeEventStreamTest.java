package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleRuntimeEventStreamTest {
    @Test
    void ownsOneSequenceAcrossConsecutiveActionResults() {
        BattleRuntimeEventStream stream = new BattleRuntimeEventStream();
        ShiftResolvedEvent repeated = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));

        List<BattleEventOccurrence> first = stream.record(new AppliedActionResult(List.of(repeated)));
        List<BattleEventOccurrence> second = stream.record(new AppliedActionResult(List.of(repeated)));

        assertEquals(1L, first.get(0).sequence());
        assertEquals(2L, second.get(0).sequence());
        assertEquals(repeated.stableKey(), first.get(0).event().stableKey());
        assertEquals(repeated.stableKey(), second.get(0).event().stableKey());
        assertNotEquals(first.get(0).occurrenceKey(), second.get(0).occurrenceKey());
        assertEquals(3L, stream.nextSequence());
    }

    @Test
    void separateBattlesOwnIndependentSequences() {
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(0, 0), new GridCoord(1, 0));
        BattleRuntimeEventStream firstBattle = new BattleRuntimeEventStream();
        BattleRuntimeEventStream secondBattle = new BattleRuntimeEventStream();

        assertEquals(1L, firstBattle.record(new AppliedActionResult(List.of(event))).get(0).sequence());
        assertEquals(1L, secondBattle.record(new AppliedActionResult(List.of(event))).get(0).sequence());
    }

    @Test
    void rejectsMissingActionResult() {
        BattleRuntimeEventStream stream = new BattleRuntimeEventStream();
        assertThrows(NullPointerException.class, () -> stream.record(null));
    }
}
