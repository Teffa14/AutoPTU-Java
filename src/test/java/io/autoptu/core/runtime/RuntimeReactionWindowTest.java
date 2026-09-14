package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RuntimeReactionWindowTest {
    private static final RuntimeReactionTriggerRegistry.TriggerDefinition SHIFT_TRIGGER =
            new RuntimeReactionTriggerRegistry.TriggerDefinition(
                    RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY,
                    Set.of()
            );

    @Test
    void derivesDeterministicIdentityFromRoundReactionTriggerAndAuthoritativeEvent() {
        ShiftResolvedEvent event = new ShiftResolvedEvent(
                "actor",
                new GridCoord(1, 0),
                new GridCoord(2, 0)
        );
        RuntimeReactionTriggerMatcher.TriggerMatch match = new RuntimeReactionTriggerMatcher.TriggerMatch(
                "reactor",
                "actor",
                SHIFT_TRIGGER
        );

        RuntimeReactionWindow first = RuntimeReactionWindow.from("Attack of Opportunity", 3, match, event);
        RuntimeReactionWindow second = RuntimeReactionWindow.from("attack_of_opportunity", 3, match, event);

        assertEquals(first, second);
        assertEquals("attack_of_opportunity", first.reactionKey());
        assertEquals(event.stableKey(), first.triggeringEventKey());
        assertEquals("reactor", first.reactorId());
        assertEquals("actor", first.triggeringActorId());
        assertEquals(RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY, first.triggerKind());
    }

    @Test
    void sequencedOccurrencesDistinguishRepeatedIdenticalEventsButRemainStableWhenRediscovered() {
        ShiftResolvedEvent event = new ShiftResolvedEvent(
                "actor",
                new GridCoord(1, 0),
                new GridCoord(2, 0)
        );
        RuntimeReactionTriggerMatcher.TriggerMatch match = new RuntimeReactionTriggerMatcher.TriggerMatch(
                "reactor",
                "actor",
                SHIFT_TRIGGER
        );
        BattleEventOccurrence firstOccurrence = new BattleEventOccurrence(41, event);
        BattleEventOccurrence secondOccurrence = new BattleEventOccurrence(42, event);

        RuntimeReactionWindow first = RuntimeReactionWindow.from(
                "attack_of_opportunity", 3, match, firstOccurrence);
        RuntimeReactionWindow rediscovered = RuntimeReactionWindow.from(
                "Attack of Opportunity", 3, match, firstOccurrence);
        RuntimeReactionWindow repeatedEvent = RuntimeReactionWindow.from(
                "attack_of_opportunity", 3, match, secondOccurrence);

        assertEquals(first, rediscovered);
        assertNotEquals(first.windowKey(), repeatedEvent.windowKey());
        assertEquals(firstOccurrence.occurrenceKey(), first.triggeringEventKey());
        assertEquals(secondOccurrence.occurrenceKey(), repeatedEvent.triggeringEventKey());
    }

    @Test
    void distinguishesDifferentReactorsRoundsAndSemanticEvents() {
        ShiftResolvedEvent firstEvent = new ShiftResolvedEvent(
                "actor",
                new GridCoord(1, 0),
                new GridCoord(2, 0)
        );
        ShiftResolvedEvent secondEvent = new ShiftResolvedEvent(
                "actor",
                new GridCoord(2, 0),
                new GridCoord(3, 0)
        );
        RuntimeReactionTriggerMatcher.TriggerMatch reactorA = new RuntimeReactionTriggerMatcher.TriggerMatch(
                "reactor-a",
                "actor",
                SHIFT_TRIGGER
        );
        RuntimeReactionTriggerMatcher.TriggerMatch reactorB = new RuntimeReactionTriggerMatcher.TriggerMatch(
                "reactor-b",
                "actor",
                SHIFT_TRIGGER
        );

        RuntimeReactionWindow base = RuntimeReactionWindow.from("attack_of_opportunity", 2, reactorA, firstEvent);

        assertNotEquals(base.windowKey(), RuntimeReactionWindow.from(
                "attack_of_opportunity", 2, reactorB, firstEvent).windowKey());
        assertNotEquals(base.windowKey(), RuntimeReactionWindow.from(
                "attack_of_opportunity", 3, reactorA, firstEvent).windowKey());
        assertNotEquals(base.windowKey(), RuntimeReactionWindow.from(
                "attack_of_opportunity", 2, reactorA, secondEvent).windowKey());
    }
}
