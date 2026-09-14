package io.autoptu.core.runtime;

import io.autoptu.core.hook.ActionWindow;
import io.autoptu.core.hook.ActionWindowTrigger;
import io.autoptu.core.hook.ShiftReactionWindowDiscovery;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeShiftReactionWindowDiscoveryTest {
    @Test
    void derivesOrderedOpposingReactorsFromCanonicalBattleState() {
        RuntimeCombatantState shifted = combatant("shifted", 2, 1, 10);
        RuntimeCombatantState beta = combatant("beta", 2, 2, 10);
        RuntimeCombatantState ally = combatant("ally", 1, 1, 10);
        RuntimeCombatantState alpha = combatant("alpha", 1, 2, 10);
        RuntimeCombatantState inactive = combatant("inactive", 2, 0, 10);
        RuntimeCombatantState fainted = combatant("fainted", 3, 1, 0);
        RuntimeCombatantState nearDestination = combatant("near-destination", 4, 2, 10);

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(shifted, beta, ally, alpha, inactive, fainted, nearDestination),
                Map.of(),
                Map.of(),
                Map.of(
                        "shifted", new CombatantGeometryState("Medium"),
                        "beta", new CombatantGeometryState("Medium"),
                        "alpha", new CombatantGeometryState("Large")
                ),
                Map.of(
                        "shifted", CombatantAffiliationState.active("red"),
                        "beta", CombatantAffiliationState.active("blue"),
                        "ally", CombatantAffiliationState.active("red"),
                        "alpha", CombatantAffiliationState.active("blue"),
                        "inactive", new CombatantAffiliationState("blue", false),
                        "fainted", CombatantAffiliationState.active("blue"),
                        "near-destination", CombatantAffiliationState.active("blue")
                )
        );

        List<ShiftReactionWindowDiscovery.DiscoveredWindow> discovered = RuntimeShiftReactionWindowDiscovery.discover(
                state,
                "shifted",
                new GridCoord(2, 1),
                new GridCoord(5, 1),
                "shift:shifted"
        );

        assertEquals(List.of("beta", "alpha"),
                discovered.stream().map(ShiftReactionWindowDiscovery.DiscoveredWindow::reactingCombatantId).toList());
        for (ShiftReactionWindowDiscovery.DiscoveredWindow window : discovered) {
            assertEquals(ActionWindow.BEFORE_ACTION, window.context().window());
            assertEquals("shifted", window.context().actingCombatantId());
            assertEquals("shift:shifted", window.context().triggerKey());
            assertEquals(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY, window.context().trigger());
        }
    }

    @Test
    void staysAdjacentProducesNoRuntimeWindow() {
        RuntimeCombatantState shifted = combatant("shifted", 2, 1, 10);
        RuntimeCombatantState foe = combatant("foe", 1, 1, 10);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(shifted, foe),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "shifted", CombatantAffiliationState.active("red"),
                        "foe", CombatantAffiliationState.active("blue")
                )
        );

        assertTrue(RuntimeShiftReactionWindowDiscovery.discover(
                state,
                "shifted",
                new GridCoord(2, 1),
                new GridCoord(2, 2),
                "shift:shifted"
        ).isEmpty());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                hp,
                10,
                new ActionBudget()
        );
    }
}
