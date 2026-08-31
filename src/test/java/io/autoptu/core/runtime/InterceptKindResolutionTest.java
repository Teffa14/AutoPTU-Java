package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptKindResolutionTest {
    @Test
    void onlyNormalizedMeleeRemainsMelee() {
        assertEquals("melee", InterceptKindResolution.fromNormalizedTargetKind("melee"));
        assertTrue(InterceptKindResolution.isMelee(" MELEE "));
    }

    @Test
    void everyOtherNormalizedTargetKindUsesRangedIntercept() {
        assertEquals("ranged", InterceptKindResolution.fromNormalizedTargetKind("ranged"));
        assertEquals("ranged", InterceptKindResolution.fromNormalizedTargetKind("burst"));
        assertEquals("ranged", InterceptKindResolution.fromNormalizedTargetKind("cone"));
        assertEquals("ranged", InterceptKindResolution.fromNormalizedTargetKind("line"));
        assertFalse(InterceptKindResolution.isMelee("blast"));
    }

    @Test
    void missingNormalizedKindIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> InterceptKindResolution.fromNormalizedTargetKind(null));
        assertThrows(IllegalArgumentException.class, () -> InterceptKindResolution.fromNormalizedTargetKind("  "));
    }
}
