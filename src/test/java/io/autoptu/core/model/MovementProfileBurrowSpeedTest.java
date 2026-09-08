package io.autoptu.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementProfileBurrowSpeedTest {
    @Test
    void preservesNumericBurrowThresholdsForAuthoritativeRules() {
        MovementProfile burrowThree = profile(3);
        MovementProfile burrowFour = profile(4);

        assertEquals(3, burrowThree.burrowSpeed());
        assertEquals(4, burrowFour.burrowSpeed());
        assertTrue(burrowThree.canBurrow());
        assertTrue(burrowFour.canBurrow());
    }

    @Test
    void positionChangesPreserveResolvedBurrowSpeed() {
        MovementProfile profile = profile(4);

        MovementProfile moved = profile.withPosition(new GridCoord(4, 2));

        assertEquals(4, moved.burrowSpeed());
        assertTrue(moved.canBurrow());
        assertEquals(new GridCoord(4, 2), moved.position());
    }

    @Test
    void legacyBooleanCapabilityRemainsCompatibleWithoutInventingHighSpeed() {
        MovementProfile legacyBurrow = new MovementProfile(
                new GridCoord(0, 0),
                6,
                0,
                0,
                1.0,
                false,
                false,
                true,
                false,
                false,
                false,
                0
        );
        MovementProfile noBurrow = MovementProfile.walking(new GridCoord(0, 0), 6);

        assertEquals(1, legacyBurrow.burrowSpeed());
        assertTrue(legacyBurrow.canBurrow());
        assertEquals(0, noBurrow.burrowSpeed());
        assertFalse(noBurrow.canBurrow());
    }

    private static MovementProfile profile(int burrowSpeed) {
        return new MovementProfile(
                new GridCoord(1, 1),
                6,
                0,
                0,
                burrowSpeed,
                1.0,
                false,
                false,
                false,
                false,
                false,
                false,
                0
        );
    }
}
