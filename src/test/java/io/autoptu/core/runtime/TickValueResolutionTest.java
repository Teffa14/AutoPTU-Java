package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TickValueResolutionTest {
    @Test
    void floorsOneTenthOfMaximumHp() {
        assertEquals(1, TickValueResolution.resolve(19));
        assertEquals(2, TickValueResolution.resolve(20));
        assertEquals(9, TickValueResolution.resolve(99));
        assertEquals(10, TickValueResolution.resolve(100));
    }

    @Test
    void enforcesMinimumTickOfOne() {
        assertEquals(1, TickValueResolution.resolve(1));
        assertEquals(1, TickValueResolution.resolve(9));
    }

    @Test
    void rejectsNonPositiveMaximumHp() {
        assertThrows(IllegalArgumentException.class, () -> TickValueResolution.resolve(0));
        assertThrows(IllegalArgumentException.class, () -> TickValueResolution.resolve(-1));
    }
}
