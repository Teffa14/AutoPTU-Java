package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeStatusStateTest {
    @Test
    void legacyStatusNamesBecomeStructuredEntriesWithoutBreakingCompatibility() {
        BattleRuntimeState state = state(Map.of("actor", Set.of("Burned")));

        assertEquals(Set.of("burned"), state.statuses("actor"));
        assertTrue(state.hasStatus("actor", "BURNED"));
        StatusEntry entry = state.statusEntry("actor", "burned").orElseThrow();
        assertTrue(entry.payload().isEmpty());
    }

    @Test
    void structuredStatusReplacementDrivesLegacyAndMetadataViewsTogether() {
        BattleRuntimeState state = state(Map.of("actor", Set.of("Burned")));
        StatusEntry flinch = new StatusEntry("Flinched", Map.of(
                "applied_round", 3,
                "source", "move:fake-out"
        ));

        state.replaceStatusEntries("actor", List.of(flinch));

        assertFalse(state.hasStatus("actor", "burned"));
        assertTrue(state.hasStatus("actor", "flinched"));
        assertEquals(Set.of("flinched"), state.statuses("actor"));
        assertEquals(3, state.statusEntry("actor", "flinched").orElseThrow()
                .intPayload("applied_round").orElseThrow());
    }

    @Test
    void authoritativeStatusMutationKeepsMetadataAndNamesInSync() {
        BattleRuntimeState state = state(Map.of());
        state.putStatus("actor", new StatusEntry("Confused", Map.of("remaining", 2)));
        state.putStatus("actor", new StatusEntry("Burned"));

        assertEquals(Set.of("confused", "burned"), state.statuses("actor"));
        assertEquals(2, state.statusEntry("actor", "confused").orElseThrow()
                .intPayload("remaining").orElseThrow());
        assertTrue(state.removeStatus("actor", "CONFUSED"));
        assertFalse(state.hasStatus("actor", "confused"));
        assertEquals(Set.of("burned"), state.statuses("actor"));
    }

    @Test
    void statusMutationRejectsUnknownCombatants() {
        BattleRuntimeState state = state(Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> state.putStatus("intruder", new StatusEntry("burned"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> state.replaceStatusEntries("intruder", List.of(new StatusEntry("burned")))
        );
    }

    private static BattleRuntimeState state(Map<String, ? extends Set<String>> statuses) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 1),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(actor),
                statuses
        );
    }
}
