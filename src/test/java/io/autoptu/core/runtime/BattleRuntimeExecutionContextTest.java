package io.autoptu.core.runtime;

import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void applyActionMutatesShiftAndRecordsItsOccurrenceInsideOneBoundary() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                10,
                10,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(openGrid(6, 6), List.of(actor));
        BattleRuntimeExecutionContext context = new BattleRuntimeExecutionContext(state);

        RecordedActionResult recorded = context.applyAction(
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        );

        assertEquals(new GridCoord(2, 1), actor.position());
        assertTrue(!actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, recorded.result().events().size());
        assertEquals(1, recorded.occurrences().size());
        ShiftResolvedEvent event = (ShiftResolvedEvent) recorded.result().events().getFirst();
        BattleEventOccurrence occurrence = recorded.occurrences().getFirst();
        assertSame(event, occurrence.event());
        assertEquals(new GridCoord(1, 1), event.origin());
        assertEquals(new GridCoord(2, 1), event.destination());
        assertEquals(1L, occurrence.sequence());
        assertEquals(2L, context.nextEventSequence());
    }

    @Test
    void rejectedShiftDoesNotConsumeOccurrenceSequence() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                10,
                10,
                new ActionBudget()
        );
        BattleRuntimeExecutionContext context = new BattleRuntimeExecutionContext(
                new BattleRuntimeState(
                        new MovementGrid(6, 6, Set.of(new GridCoord(2, 1)), Map.of()),
                        List.of(actor)
                ));

        assertThrows(IllegalArgumentException.class, () -> context.applyAction(
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        ));
        assertEquals(new GridCoord(1, 1), actor.position());
        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1L, context.nextEventSequence());
    }

    @Test
    void applyAndRecordKeepsResultAndOccurrenceIdentityInsideOneBoundary() {
        BattleRuntimeExecutionContext context = new BattleRuntimeExecutionContext(
                new BattleRuntimeState(openGrid(4, 4), List.of()));
        ShiftResolvedEvent firstEvent = new ShiftResolvedEvent("actor", new GridCoord(0, 0), new GridCoord(1, 0));
        ShiftResolvedEvent secondEvent = new ShiftResolvedEvent("actor", new GridCoord(1, 0), new GridCoord(2, 0));
        AppliedActionResult firstResult = new AppliedActionResult(List.of(firstEvent));
        AppliedActionResult secondResult = new AppliedActionResult(List.of(secondEvent));

        RecordedActionResult first = context.applyAndRecord(() -> firstResult);
        RecordedActionResult second = context.applyAndRecord(() -> secondResult);

        assertSame(firstResult, first.result());
        assertSame(secondResult, second.result());
        assertEquals(List.of(firstEvent), first.result().events());
        assertEquals(1L, first.occurrences().get(0).sequence());
        assertEquals(2L, second.occurrences().get(0).sequence());
        assertEquals(firstEvent.stableKey(), first.occurrences().get(0).event().stableKey());
        assertEquals(secondEvent.stableKey(), second.occurrences().get(0).event().stableKey());
        assertEquals(3L, context.nextEventSequence());
    }

    @Test
    void applyAndRecordRejectsMissingApplicationOrResultWithoutAdvancingSequence() {
        BattleRuntimeExecutionContext context = new BattleRuntimeExecutionContext(
                new BattleRuntimeState(openGrid(2, 2), List.of()));

        assertThrows(NullPointerException.class, () -> context.applyAndRecord(null));
        assertThrows(NullPointerException.class, () -> context.applyAndRecord(() -> null));
        assertEquals(1L, context.nextEventSequence());
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
