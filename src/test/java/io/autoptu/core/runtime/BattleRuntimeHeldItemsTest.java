package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeHeldItemsTest {
    @Test
    void heldItemsAreDefensivelyCopiedIntoAuthoritativeSnapshot() {
        ArrayList<HeldItemState> items = new ArrayList<>();
        items.add(new HeldItemState("slot-0", "Pink Pearl"));
        BattleRuntimeState state = state(items);

        items.clear();

        assertTrue(state.hasCanonicalHeldItems("actor"));
        assertEquals(List.of(new HeldItemState("slot-0", "Pink Pearl")), state.heldItems("actor"));
    }

    @Test
    void duplicateStableItemIdsAreRejected() {
        List<HeldItemState> duplicate = List.of(
                new HeldItemState("slot-0", "Pink Pearl"),
                new HeldItemState("slot-0", "Megaphone")
        );

        assertThrows(IllegalArgumentException.class, () -> state(duplicate));
    }

    @Test
    void absentAndCanonicalEmptyItemStateRemainDistinguishable() {
        RuntimeCombatantState actor = actor();
        BattleRuntimeState absent = new BattleRuntimeState(
                grid(), List.of(actor)
        );
        BattleRuntimeState canonicalEmpty = state(List.of());

        assertTrue(absent.heldItems("actor").isEmpty());
        assertTrue(canonicalEmpty.heldItems("actor").isEmpty());
        assertEquals(false, absent.hasCanonicalHeldItems("actor"));
        assertTrue(canonicalEmpty.hasCanonicalHeldItems("actor"));
    }

    private static BattleRuntimeState state(List<HeldItemState> items) {
        return new BattleRuntimeState(
                grid(),
                List.of(actor()),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("actor", items)
        );
    }

    private static RuntimeCombatantState actor() {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                new ActionBudget()
        );
    }

    private static MovementGrid grid() {
        return new MovementGrid(4, 4, Set.of(), Map.of());
    }
}
