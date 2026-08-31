package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatantRuleContentRegistryTest {
    @Test
    void resolvesCanonicalContentAndDefaultsUnknownCombatantsToEmpty() {
        CombatantRuleContent expected = new CombatantRuleContent(
                List.of("Living Weapon"), 4, "trainer-a", Map.of("athletics", 3), List.of("Sentinel Stance"), List.of()
        );
        CombatantRuleContentRegistry registry = new CombatantRuleContentRegistry(Map.of("ally", expected));

        assertEquals(expected, registry.require("ally"));
        assertEquals(CombatantRuleContent.empty(), registry.require("missing"));
    }

    @Test
    void rejectsInvalidCanonicalEntries() {
        assertThrows(IllegalArgumentException.class, () ->
                new CombatantRuleContentRegistry(Map.of("", CombatantRuleContent.empty())));
    }
}
