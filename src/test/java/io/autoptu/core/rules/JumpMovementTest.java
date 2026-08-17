package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.JumpProfile;
import io.autoptu.core.model.MovementGrid;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JumpMovementTest {
    @Test
    void longJumpUsesChebyshevRange() {
        MovementGrid grid = new MovementGrid(7, 7, Set.of(), Map.of());
        JumpProfile actor = JumpProfile.basic(new GridCoord(3, 3), 2, 0);
        Set<GridCoord> result = Movement.legalLongJumpTiles(grid, actor);

        assertTrue(result.contains(new GridCoord(5, 5)));
        assertFalse(result.contains(new GridCoord(6, 3)));
    }

    @Test
    void wallrunnerMayCrossBlockedPathAndExtendLongJump() {
        MovementGrid grid = new MovementGrid(
                5, 1,
                Set.of(new GridCoord(1, 0)),
                Map.of()
        );
        JumpProfile actor = new JumpProfile(
                new GridCoord(0, 0), 1, 0,
                false, false, false, false, false, 1
        );
        Set<GridCoord> result = Movement.legalLongJumpTiles(grid, actor);

        assertFalse(result.contains(new GridCoord(1, 0)));
        assertTrue(result.contains(new GridCoord(2, 0)));
    }

    @Test
    void blockedLongJumpPathFailsWithoutWallrunner() {
        MovementGrid grid = new MovementGrid(
                4, 1,
                Set.of(new GridCoord(1, 0)),
                Map.of()
        );
        JumpProfile actor = JumpProfile.basic(new GridCoord(0, 0), 2, 0);
        Set<GridCoord> result = Movement.legalLongJumpTiles(grid, actor);

        assertFalse(result.contains(new GridCoord(2, 0)));
    }

    @Test
    void longJumpWaterLandingRequiresSwimOrFlight() {
        GridCoord water = new GridCoord(2, 0);
        MovementGrid grid = new MovementGrid(4, 1, Set.of(), Map.of(water, "Water"));

        JumpProfile dry = JumpProfile.basic(new GridCoord(0, 0), 3, 0);
        assertFalse(Movement.legalLongJumpTiles(grid, dry).contains(water));

        JumpProfile swimmer = new JumpProfile(
                new GridCoord(0, 0), 3, 0,
                false, true, false, false, false, 0
        );
        assertTrue(Movement.legalLongJumpTiles(grid, swimmer).contains(water));
    }

    @Test
    void burrowDoesNotIgnoreIntermediateJumpBlockersInPython() {
        MovementGrid grid = new MovementGrid(
                4, 1,
                Set.of(new GridCoord(1, 0)),
                Map.of()
        );
        JumpProfile burrower = new JumpProfile(
                new GridCoord(0, 0), 2, 0,
                false, false, true, false, false, 0
        );
        Set<GridCoord> result = Movement.legalLongJumpTiles(grid, burrower);

        assertTrue(result.contains(new GridCoord(1, 0)));
        assertFalse(result.contains(new GridCoord(2, 0)));
    }

    @Test
    void highJumpMatchesPythonWaterBehavior() {
        GridCoord water = new GridCoord(1, 0);
        MovementGrid grid = new MovementGrid(3, 1, Set.of(), Map.of(water, "Water"));
        JumpProfile actor = JumpProfile.basic(new GridCoord(0, 0), 0, 1);

        assertTrue(Movement.legalHighJumpTiles(grid, actor).contains(water));
    }

    @Test
    void highJumpRejectsBlockedDestinationWithoutTraversalCapability() {
        GridCoord wall = new GridCoord(1, 0);
        MovementGrid grid = new MovementGrid(3, 1, Set.of(wall), Map.of());
        JumpProfile actor = JumpProfile.basic(new GridCoord(0, 0), 0, 1);

        assertFalse(Movement.legalHighJumpTiles(grid, actor).contains(wall));
    }

    @Test
    void fitPredicateRejectsJumpLanding() {
        GridCoord forbidden = new GridCoord(1, 0);
        MovementGrid grid = new MovementGrid(3, 1, Set.of(), Map.of());
        JumpProfile actor = JumpProfile.basic(new GridCoord(0, 0), 2, 0);

        assertFalse(Movement.legalLongJumpTiles(
                grid,
                actor,
                coord -> !coord.equals(forbidden)
        ).contains(forbidden));
    }
}
