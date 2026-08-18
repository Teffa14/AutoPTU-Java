package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusSkipExceptionResolutionTest {
    @Test
    void supremeConcentrationBypassesCoveredStatusAndPreservesSignatureMove() {
        var result = StatusSkipExceptionResolution.resolve(
                "Flinch",
                "Supreme Concentration",
                "Thunderbolt",
                false
        );

        assertFalse(result.skipTurn());
        assertEquals(StatusSkipExceptionResolution.ExceptionKind.SUPREME_CONCENTRATION, result.exceptionKind());
        assertEquals("Thunderbolt", result.signatureMove());
    }

    @Test
    void supremeConcentrationDoesNotBypassSleep() {
        var result = StatusSkipExceptionResolution.resolve(
                "Sleep",
                "supreme_concentration",
                "Thunderbolt",
                false
        );

        assertTrue(result.skipTurn());
        assertEquals(StatusSkipExceptionResolution.ExceptionKind.NONE, result.exceptionKind());
    }

    @Test
    void duelistsManualBypassesCoveredVolatileStatus() {
        var result = StatusSkipExceptionResolution.resolve("Confused", "", "", true);

        assertFalse(result.skipTurn());
        assertEquals(StatusSkipExceptionResolution.ExceptionKind.DUELISTS_MANUAL, result.exceptionKind());
    }

    @Test
    void supremeConcentrationWinsWhenBothExceptionsApply() {
        var result = StatusSkipExceptionResolution.resolve(
                "Confusion",
                "Supreme-Concentration",
                "Psychic",
                true
        );

        assertFalse(result.skipTurn());
        assertEquals(StatusSkipExceptionResolution.ExceptionKind.SUPREME_CONCENTRATION, result.exceptionKind());
        assertEquals("Psychic", result.signatureMove());
    }
}
