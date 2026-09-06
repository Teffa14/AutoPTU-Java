package io.autoptu.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TrainerFeatureEventStructuredDetailsTest {
    @Test
    void preservesOrderedScalarListPayloadForBorrowedMoveExpiry() {
        ArrayList<String> moves = new ArrayList<>(List.of("Confusion", "Psybeam"));
        TrainerFeatureEvent event = new TrainerFeatureEvent(
                "actor",
                "Psionic Sponge",
                "borrow_move_end",
                Map.of("moves", moves)
        );

        moves.add("Tackle");

        assertEquals(List.of("Confusion", "Psybeam"), event.details().get("moves"));
        assertEquals(
                "trainer_feature|actor|psionic sponge|borrow_move_end|||0|moves=[Confusion, Psybeam]",
                event.stableKey()
        );
    }

    @Test
    void structuredPayloadIsImmutableToConsumers() {
        TrainerFeatureEvent event = new TrainerFeatureEvent(
                "actor",
                "Psionic Sponge",
                "borrow_move_end",
                Map.of("moves", List.of("Confusion"))
        );

        @SuppressWarnings("unchecked")
        List<Object> moves = (List<Object>) event.details().get("moves");

        assertThrows(UnsupportedOperationException.class, () -> moves.add("Psybeam"));
        assertThrows(UnsupportedOperationException.class, () -> event.details().put("extra", true));
    }

    @Test
    void rejectsNestedStructuredValuesOutsideTheFrozenContract() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrainerFeatureEvent(
                        "actor",
                        "Psionic Sponge",
                        "borrow_move_end",
                        Map.of("moves", List.of(Map.of("name", "Confusion")))
                )
        );
    }

    @Test
    void existingScalarDetailsRemainSupported() {
        TrainerFeatureEvent event = new TrainerFeatureEvent(
                "actor",
                "Feature",
                "effect",
                Map.of("amount", 2, "phase", "END", "active", true)
        );

        assertEquals(2, event.amount());
        assertEquals("END", event.phase());
        assertEquals(true, event.details().get("active"));
    }
}
