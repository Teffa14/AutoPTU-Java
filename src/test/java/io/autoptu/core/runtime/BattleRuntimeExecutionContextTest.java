package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleRuntimeExecutionContextTest {
    @Test
    void ownsStateAndOneOccurrenceSequenceAcrossActions() {
        BattleRuntimeState state = new BattleRuntimeState(openGrid(4, 4), List.of());
        BattleRuntimeExecutionContext context = new BattleRuntimeExecutionContext(state);
        ShiftResolvedEvent repeated = new ShiftResolvedEvent("actor", new GridCoord(0, 0), new GridCoord(1, 0));

        List<BattleEventOccurrence> first = context.record(new AppliedActionResult(List.of(repeated)));
        List<BattleEventOccurrence> second = context.record(new AppliedActionResult(List.of(repeated)));

        assertSame(state, context.state());
        assertEquals(1L, first.get(0).sequence());
        assertEquals(2L, second.get(0).sequence());
        assertNotEquals(first.get(0).occurrenceKey(), second.get(0).occurrenceKey());
        assertEquals(3L, context.nextEventSequence());
    }

    @Test
    void separateBattleContextsOwnIndependentSequences() {
        ShiftResolvedEvent event = new ShiftResolvedEvent("actor", new GridCoord(0, 0), new GridCoord(1, 0));
        BattleRuntimeExecutionContext first = new BattleRuntimeExecutionContext(
                new BattleRuntimeState(openGrid(2, 2), List.of()));
        BattleRuntimeExecutionContext second = new BattleRuntimeExecutionContext(
                new BattleRuntimeState(openGrid(2, 2), List.of()));

        assertEquals(1L, first.record(new AppliedActionResult(List.of(event))).get(0).sequence());
        assertEquals(1L, second.record(new AppliedActionResult(List.of(event))).get(0).sequence());
    }

    @Test
    void rejectsMissingBattleState() {
        assertThrows(NullPointerException.class, () -> new BattleRuntimeExecutionContext(null));
    }

    private static MovementGrid openGrid(int width, int height) {
        return new MovementGrid(width, height, Set.of(), Map.of());
    }
}
