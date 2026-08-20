package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityIdentityResolutionTest {
    @Test
    void errataVariantSatisfiesBaseAndExactErrataRegistrations() {
        List<String> abilities = List.of("Mega Launcher [Errata]");

        assertTrue(AbilityIdentityResolution.matchesRegistration(abilities, "Mega Launcher"));
        assertTrue(AbilityIdentityResolution.matchesRegistration(abilities, "Mega Launcher [Errata]"));
    }

    @Test
    void baseAbilityDoesNotSatisfyErrataSpecificRegistration() {
        List<String> abilities = List.of("Mega Launcher");

        assertTrue(AbilityIdentityResolution.matchesRegistration(abilities, "Mega Launcher"));
        assertFalse(AbilityIdentityResolution.matchesRegistration(abilities, "Mega Launcher [Errata]"));
    }

    @Test
    void matchingIsCaseInsensitiveButDoesNotMatchUnrelatedNames() {
        assertTrue(AbilityIdentityResolution.matchesRegistration(
                List.of("MEGA LAUNCHER [ERRATA]"), "mega launcher"));
        assertFalse(AbilityIdentityResolution.matchesRegistration(
                List.of("Mega Launcher [Errata]"), "No Guard"));
    }

    @Test
    void exactMatchingDoesNotTreatErrataAsBaseAbility() {
        assertTrue(AbilityIdentityResolution.matchesExact(List.of(" Aura Break "), "aura break"));
        assertFalse(AbilityIdentityResolution.matchesExact(List.of("Aura Break [Errata]"), "Aura Break"));
    }

    @Test
    void exactMatchingPreservesPythonHugePurePowerErrataEquivalences() {
        assertTrue(AbilityIdentityResolution.matchesExact(
                List.of("Huge Power / Pure Power [Errata]"), "Huge Power [Errata]"));
        assertTrue(AbilityIdentityResolution.matchesExact(
                List.of("Pure Power [Errata]"), "Huge Power / Pure Power [Errata]"));
        assertFalse(AbilityIdentityResolution.matchesExact(
                List.of("Huge Power [Errata]"), "Pure Power [Errata]"));
    }
}