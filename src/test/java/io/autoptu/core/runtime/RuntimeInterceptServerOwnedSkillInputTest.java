package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeInterceptServerOwnedSkillInputTest {
    @Test
    void derivesInterceptSkillsFromServerOwnedCombatantContent() {
        CombatantRuleContent content = new CombatantRuleContent(
                List.of(),
                3,
                "trainer-1",
                Map.of("Acrobatics", 4, "Athletics", 6)
        );

        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckApplication.fromServerOwnedSkills(
                5,
                content,
                2,
                -1,
                true
        );

        assertEquals(5, input.distance());
        assertEquals(4, input.acrobaticsRank());
        assertEquals(6, input.athleticsRank());
        assertEquals(2, input.justifiedBonus());
        assertEquals(-1, input.terrainBonus());
        assertEquals(true, input.coachingAutomaticSuccess());
    }

    @Test
    void missingSkillsUsePtuZeroDefaultAndContentIsRequired() {
        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckApplication.fromServerOwnedSkills(
                1,
                CombatantRuleContent.empty(),
                0,
                0,
                false
        );

        assertEquals(0, input.acrobaticsRank());
        assertEquals(0, input.athleticsRank());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeInterceptCheckApplication.fromServerOwnedSkills(1, null, 0, 0, false)
        );
    }

    @Test
    void serverOwnedSkillBuilderIsNotPublicAdapterApi() throws Exception {
        Method method = RuntimeInterceptCheckApplication.class.getDeclaredMethod(
                "fromServerOwnedSkills",
                int.class,
                CombatantRuleContent.class,
                int.class,
                int.class,
                boolean.class
        );
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }
}
