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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcedDisplacementResolutionTest {
    @Test
    void advancesStepwiseUntilRequestedDistanceWhenClear() {
        BattleRuntimeState state = state(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                combatant("target", 1, 1),
                Map.of()
        );

        ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                state, "target", new GridCoord(1, 0), 3
        );

        assertEquals(new GridCoord(4, 1), result.destination());
        assertEquals(3, result.movedDistance());
        assertFalse(result.stoppedEarly());
        assertEquals(ForcedDisplacementResolution.StopReason.NONE, result.stop().reason());
        assertEquals(List.of(new GridCoord(2, 1), new GridCoord(3, 1), new GridCoord(4, 1)), result.traversedAnchors());
    }

    @Test
    void stopsAtLastLegalAnchorBeforeGridBlocker() {
        BattleRuntimeState state = state(
                new MovementGrid(8, 4, Set.of(new GridCoord(4, 1)), Map.of()),
                combatant("target", 1, 1),
                Map.of()
        );

        ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                state, "target", new GridCoord(1, 0), 5
        );

        assertEquals(new GridCoord(3, 1), result.destination());
        assertEquals(2, result.movedDistance());
        assertTrue(result.stoppedEarly());
        assertEquals(ForcedDisplacementResolution.StopReason.BLOCKER, result.stop().reason());
        assertEquals(new GridCoord(4, 1), result.stop().attemptedAnchor());
        assertEquals(new GridCoord(4, 1), result.stop().blockingTile());
        assertNull(result.stop().blockingCombatantId());
    }

    @Test
    void stopsBeforeLivingCombatantFootprint() {
        RuntimeCombatantState target = combatant("target", 1, 1);
        RuntimeCombatantState blocker = combatant("blocker", 4, 1);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(target, blocker)
        );

        ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                state, "target", new GridCoord(1, 0), 5
        );

        assertEquals(new GridCoord(3, 1), result.destination());
        assertEquals(2, result.movedDistance());
        assertEquals(ForcedDisplacementResolution.StopReason.OCCUPIED, result.stop().reason());
        assertEquals("blocker", result.stop().blockingCombatantId());
        assertEquals(new GridCoord(4, 1), result.stop().blockingTile());
    }

    @Test
    void faintedCombatantsDoNotBlockForcedDisplacement() {
        RuntimeCombatantState target = combatant("target", 1, 1);
        RuntimeCombatantState fainted = combatant("fainted", 3, 1);
        fainted.setHp(0);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(target, fainted)
        );

        ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                state, "target", new GridCoord(1, 0), 3
        );

        assertEquals(new GridCoord(4, 1), result.destination());
        assertEquals(3, result.movedDistance());
        assertEquals(ForcedDisplacementResolution.StopReason.NONE, result.stop().reason());
    }

    @Test
    void largeFootprintStopsBeforeOnlyPartOfBodyWouldCrossBlocker() {
        RuntimeCombatantState target = combatant("target", 1, 1);
        BattleRuntimeState state = state(
                new MovementGrid(8, 5, Set.of(new GridCoord(4, 2)), Map.of()),
                target,
                Map.of("target", new CombatantGeometryState("Large"))
        );

        ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                state, "target", new GridCoord(1, 0), 5
        );

        assertEquals(new GridCoord(2, 1), result.destination());
        assertEquals(1, result.movedDistance());
        assertTrue(result.stoppedEarly());
        assertEquals(ForcedDisplacementResolution.StopReason.BLOCKER, result.stop().reason());
        assertEquals(new GridCoord(3, 1), result.stop().attemptedAnchor());
        assertEquals(new GridCoord(4, 2), result.stop().blockingTile());
    }

    @Test
    void boundaryStopsLargeFootprintBeforeAnyTileLeavesGrid() {
        RuntimeCombatantState target = combatant("target", 1, 1);
        BattleRuntimeState state = state(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                target,
                Map.of("target", new CombatantGeometryState("Large"))
        );

        ForcedDisplacementResolution.Result result = ForcedDisplacementResolution.resolve(
                state, "target", new GridCoord(-1, 0), 5
        );

        assertEquals(new GridCoord(0, 1), result.destination());
        assertEquals(1, result.movedDistance());
        assertTrue(result.stoppedEarly());
        assertEquals(ForcedDisplacementResolution.StopReason.OUT_OF_BOUNDS, result.stop().reason());
        assertEquals(new GridCoord(-1, 1), result.stop().attemptedAnchor());
        assertEquals(new GridCoord(-1, 1), result.stop().blockingTile());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(
            MovementGrid grid,
            RuntimeCombatantState combatant,
            Map<String, CombatantGeometryState> geometry
    ) {
        return new BattleRuntimeState(
                grid,
                List.of(combatant),
                Map.of(),
                Map.of(),
                geometry
        );
    }
}
