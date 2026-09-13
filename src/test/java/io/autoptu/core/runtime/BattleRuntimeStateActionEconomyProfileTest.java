package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionEconomyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleRuntimeStateActionEconomyProfileTest {
    @Test
    void emptyBattleOwnsPythonCompatibilityProfile() {
        BattleRuntimeState state = new BattleRuntimeState(grid(), List.of());

        assertEquals(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY, state.actionEconomyProfile());
    }

    @Test
    void homogeneousPythonBattleOwnsPythonCompatibilityProfile() {
        BattleRuntimeState state = new BattleRuntimeState(grid(), List.of(
                combatant("one", ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY),
                combatant("two", ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY)
        ));

        assertEquals(ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY, state.actionEconomyProfile());
    }

    @Test
    void homogeneousKairosBattleOwnsKairosProfile() {
        BattleRuntimeState state = new BattleRuntimeState(grid(), List.of(
                combatant("one", ActionEconomyProfile.KAIROS_2_1_25_1),
                combatant("two", ActionEconomyProfile.KAIROS_2_1_25_1)
        ));

        assertEquals(ActionEconomyProfile.KAIROS_2_1_25_1, state.actionEconomyProfile());
    }

    @Test
    void mixedProfilesAreRejectedAtAuthoritativeSnapshotConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new BattleRuntimeState(grid(), List.of(
                combatant("one", ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY),
                combatant("two", ActionEconomyProfile.KAIROS_2_1_25_1)
        )));
    }

    private static MovementGrid grid() {
        return new MovementGrid(4, 4, Set.of(), Map.of());
    }

    private static RuntimeCombatantState combatant(String id, ActionEconomyProfile profile) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(0, 0), 3),
                10,
                10,
                new ActionBudget(profile)
        );
    }
}
