package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeInterceptCheckInputFactoryTest {
    @Test
    void derivesSkillsAndCoachingFromServerOwnedState() {
        RuntimeCombatantState interceptor = combatant("interceptor");
        interceptor.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(interceptor);
        CombatantRuleContent content = new CombatantRuleContent(
                List.of(),
                4,
                "trainer-a",
                Map.of("Acrobatics", 3, "Athletics", 7)
        );

        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state,
                "interceptor",
                content,
                5,
                2,
                -1
        );

        assertEquals(5, input.distance());
        assertEquals(3, input.acrobaticsRank());
        assertEquals(7, input.athleticsRank());
        assertEquals(2, input.justifiedBonus());
        assertEquals(-1, input.terrainBonus());
        assertTrue(input.coachingAutomaticSuccess());
    }

    @Test
    void absentCoachingDoesNotForceSuccess() {
        RuntimeCombatantState interceptor = combatant("interceptor");
        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor),
                "interceptor",
                CombatantRuleContent.empty(),
                2,
                0,
                0
        );

        assertFalse(input.coachingAutomaticSuccess());
        assertEquals(0, input.acrobaticsRank());
        assertEquals(0, input.athleticsRank());
    }

    @Test
    void rejectsUnknownInterceptorAndRemainsCoreOnly() throws Exception {
        BattleRuntimeState state = state(combatant("interceptor"));
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeInterceptCheckInputFactory.fromState(
                        state,
                        "missing",
                        CombatantRuleContent.empty(),
                        1,
                        0,
                        0
                )
        );

        Method method = RuntimeInterceptCheckInputFactory.class.getDeclaredMethod(
                "fromState",
                BattleRuntimeState.class,
                String.class,
                CombatantRuleContent.class,
                int.class,
                int.class,
                int.class
        );
        assertFalse(Modifier.isPublic(RuntimeInterceptCheckInputFactory.class.getModifiers()));
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }

    private static RuntimeCombatantState combatant(String id) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 6),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState interceptor) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(interceptor)
        );
    }
}
