package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementTest {
    @Test
    void neighboringTilesMatchPythonCardinalOrder() {
        assertEquals(
                java.util.List.of(
                        new GridCoord(3, 2),
                        new GridCoord(1, 2),
                        new GridCoord(2, 3),
                        new GridCoord(2, 1)
                ),
                Movement.neighboringTiles(new GridCoord(2, 2))
        );
    }

    @Test
    void stepTowardMovesBothAxesWhenNeeded() {
        assertEquals(
                java.util.List.of(
                        new GridCoord(1, 1),
                        new GridCoord(2, 2),
                        new GridCoord(3, 2),
                        new GridCoord(4, 2)
                ),
                Movement.stepToward(new GridCoord(0, 0), new GridCoord(4, 2))
        );
    }

    @Test
    void walkingReachUsesCardinalCost() {
        MovementGrid grid = new MovementGrid(7, 7, Set.of(), Map.of());
        MovementProfile actor = MovementProfile.walking(new GridCoord(3, 3), 2);
        Set<GridCoord> result = Movement.legalShiftTiles(grid, actor);

        assertEquals(13, result.size());
        assertTrue(result.contains(new GridCoord(5, 3)));
        assertTrue(result.contains(new GridCoord(4, 4)));
        assertFalse(result.contains(new GridCoord(5, 5)));
    }

    @Test
    void roughTerrainCostsTwoMovementUnlessIgnored() {
        GridCoord origin = new GridCoord(0, 0);
        GridCoord rough = new GridCoord(1, 0);
        GridCoord beyond = new GridCoord(2, 0);
        MovementGrid grid = new MovementGrid(4, 1, Set.of(), Map.of(rough, "Rough Grass"));

        MovementProfile normal = MovementProfile.walking(origin, 2);
        Set<GridCoord> normalResult = Movement.legalShiftTiles(grid, normal);
        assertTrue(normalResult.contains(rough));
        assertFalse(normalResult.contains(beyond));

        MovementProfile naturewalk = new MovementProfile(
                origin, 2, 0, 0, 1.0,
                false, false, false, false, false, true, 0
        );
        assertTrue(Movement.legalShiftTiles(grid, naturewalk).contains(beyond));
    }

    @Test
    void waterRequiresSwimAndUsesSwimLimit() {
        GridCoord origin = new GridCoord(0, 0);
        MovementGrid grid = new MovementGrid(
                4, 1, Set.of(),
                Map.of(
                        new GridCoord(1, 0), "Water",
                        new GridCoord(2, 0), "Water",
                        new GridCoord(3, 0), "Water"
                )
        );

        MovementProfile walker = MovementProfile.walking(origin, 3);
        assertEquals(Set.of(origin), Movement.legalShiftTiles(grid, walker));

        MovementProfile swimmer = new MovementProfile(
                origin, 3, 2, 0, 1.0,
                false, true, false, false, false, false, 0
        );
        Set<GridCoord> swimResult = Movement.legalShiftTiles(grid, swimmer);
        assertTrue(swimResult.contains(new GridCoord(2, 0)));
        assertFalse(swimResult.contains(new GridCoord(3, 0)));
    }

    @Test
    void mixedTerrainUsesTheDestinationTilesMovementModeLikePython() {
        GridCoord origin = new GridCoord(0, 0);
        MovementGrid grid = new MovementGrid(
                4, 1, Set.of(),
                Map.of(new GridCoord(1, 0), "Water", new GridCoord(2, 0), "Water")
        );
        MovementProfile swimmer = new MovementProfile(
                origin, 3, 2, 0, 1.0,
                false, true, false, false, false, false, 0
        );

        // The first two steps use Swim=2. Returning to land at x=3 switches
        // the destination-tile limit back to Overland=3, matching movement.py.
        assertTrue(Movement.legalShiftTiles(grid, swimmer).contains(new GridCoord(3, 0)));
    }

    @Test
    void flyingUsesSkyLimitAndIgnoresRoughAndBlockers() {
        GridCoord origin = new GridCoord(0, 0);
        MovementGrid grid = new MovementGrid(
                5, 1,
                Set.of(new GridCoord(1, 0)),
                Map.of(new GridCoord(2, 0), "Difficult Rough")
        );
        MovementProfile flyer = new MovementProfile(
                origin, 0, 0, 3, 1.0,
                true, false, false, false, false, false, 0
        );
        Set<GridCoord> result = Movement.legalShiftTiles(grid, flyer);
        assertTrue(result.contains(new GridCoord(1, 0)));
        assertTrue(result.contains(new GridCoord(2, 0)));
        assertTrue(result.contains(new GridCoord(3, 0)));
        assertFalse(result.contains(new GridCoord(4, 0)));
    }

    @Test
    void wallrunnerMayTraverseBlockedTilesButNotLandOnThem() {
        GridCoord origin = new GridCoord(0, 0);
        GridCoord wall = new GridCoord(1, 0);
        MovementGrid grid = new MovementGrid(5, 1, Set.of(wall), Map.of());
        MovementProfile actor = new MovementProfile(
                origin, 3, 0, 0, 1.0,
                false, false, false, false, false, false, 1
        );

        Set<GridCoord> result = Movement.legalShiftTiles(grid, actor);
        assertFalse(result.contains(wall));
        assertTrue(result.contains(new GridCoord(2, 0)));
        assertTrue(result.contains(new GridCoord(3, 0)));
    }

    @Test
    void sprintCeilsAfterPenaltyLikePython() {
        GridCoord origin = new GridCoord(0, 0);
        MovementGrid grid = new MovementGrid(8, 1, Set.of(), Map.of());
        MovementProfile actor = new MovementProfile(
                origin, 5, 0, 0, 1.5,
                false, false, false, false, false, false, 0
        );

        Set<GridCoord> result = Movement.legalShiftTiles(grid, actor, 2, ignored -> true);
        // Python: ceil((5 - 2) * 1.5) == 5.
        assertTrue(result.contains(new GridCoord(5, 0)));
        assertFalse(result.contains(new GridCoord(6, 0)));
    }

    @Test
    void footprintFitPredicateStopsIllegalLandingAndExpansion() {
        GridCoord origin = new GridCoord(0, 0);
        MovementGrid grid = new MovementGrid(5, 1, Set.of(), Map.of());
        MovementProfile actor = MovementProfile.walking(origin, 4);

        Set<GridCoord> result = Movement.legalShiftTiles(
                grid,
                actor,
                0,
                coord -> !coord.equals(new GridCoord(2, 0))
        );
        assertTrue(result.contains(new GridCoord(1, 0)));
        assertFalse(result.contains(new GridCoord(2, 0)));
        assertFalse(result.contains(new GridCoord(3, 0)));
    }
}
