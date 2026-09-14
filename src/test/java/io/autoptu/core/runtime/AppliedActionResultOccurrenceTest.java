package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppliedActionResultOccurrenceTest {
    @Test
    void preservesOrderedSemanticEventsWhileAssigningBattleLocalOccurrences() {
        RuntimeBattleEventSequencer sequencer = new RuntimeBattleEventSequencer();
        ShiftResolvedEvent repeated = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));
        ShiftResolvedEvent later = new ShiftResolvedEvent("actor", new GridCoord(2, 0), new GridCoord(3, 0));

        List<BattleEventOccurrence> firstAction = new AppliedActionResult(List.of(repeated, repeated))
                .recordEventOccurrences(sequencer);
        List<BattleEventOccurrence> secondAction = new AppliedActionResult(List.of(later))
                .recordEventOccurrences(sequencer);

        assertEquals(List.of(1L, 2L), firstAction.stream().map(BattleEventOccurrence::sequence).toList());
        assertEquals(List.of(repeated.stableKey(), repeated.stableKey()),
                firstAction.stream().map(occurrence -> occurrence.event().stableKey()).toList());
        assertNotEquals(firstAction.get(0).occurrenceKey(), firstAction.get(1).occurrenceKey());

        assertEquals(3L, secondAction.get(0).sequence());
        assertEquals(later.stableKey(), secondAction.get(0).event().stableKey());
        assertEquals(4L, sequencer.nextSequence());
    }

    @Test
    void requiresRuntimeOwnedSequencer() {
        AppliedActionResult result = new AppliedActionResult(List.of());
        assertThrows(NullPointerException.class, () -> result.recordEventOccurrences(null));
    }
}
