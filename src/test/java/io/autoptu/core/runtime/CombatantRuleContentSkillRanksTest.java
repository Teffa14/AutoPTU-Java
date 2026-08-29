package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CombatantRuleContentSkillRanksTest {
    @Test
    void keepsGenericPtuSkillRanksServerOwnedAndCaseInsensitive() {
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Naturewalk"),
                4,
                "trainer-1",
                Map.of("Acrobatics", 3, "ATHLETICS", 5)
        );

        assertEquals(3, content.skillRank("acrobatics"));
        assertEquals(5, content.skillRank("Athletics"));
        assertEquals(0, content.skillRank("Stealth"));
        assertEquals(0, content.skillRank(null));
    }

    @Test
    void normalizesInvalidRanksWithoutExposingMutableContent() {
        CombatantRuleContent content = new CombatantRuleContent(
                List.of(),
                null,
                "",
                Map.of("Acrobatics", -2)
        );

        assertEquals(0, content.skillRank("Acrobatics"));
        assertThrows(UnsupportedOperationException.class, () -> content.skillRanks().put("athletics", 4));
    }

    @Test
    void existingConstructorsRemainBackwardCompatible() {
        CombatantRuleContent legacy = new CombatantRuleContent(List.of("Wallclimber"), 2, "trainer-2");
        CombatantRuleContent shorter = new CombatantRuleContent(List.of(), null);

        assertEquals(Map.of(), legacy.skillRanks());
        assertEquals(Map.of(), shorter.skillRanks());
    }
}
