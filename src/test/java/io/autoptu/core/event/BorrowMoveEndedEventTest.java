package io.autoptu.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class BorrowMoveEndedEventTest {
    @Test
    void preservesOrderedNormalizedBorrowedMoveSnapshot() {
        ArrayList<String> moves = new ArrayList<>(List.of(" Psybeam ", "CONFUSION"));
        BorrowMoveEndedEvent event = new BorrowMoveEndedEvent(" actor ", moves);

        moves.add("Tackle");

        assertEquals("actor", event.combatantId());
        assertEquals(List.of("psybeam", "confusion"), event.moves());
        assertEquals(BattleEventKind.BORROW_MOVE_END, event.kind());
        assertEquals("borrow_move_end|actor|psybeam,confusion", event.stableKey());
    }

    @Test
    void moveSnapshotIsImmutableToConsumers() {
        BorrowMoveEndedEvent event = new BorrowMoveEndedEvent("actor", List.of("confusion"));

        assertThrows(UnsupportedOperationException.class, () -> event.moves().add("psybeam"));
    }

    @Test
    void rejectsMissingCombatantAndBlankMoveIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new BorrowMoveEndedEvent(" ", List.of("confusion")));
        assertThrows(IllegalArgumentException.class, () -> new BorrowMoveEndedEvent("actor", List.of(" ")));
    }
}
