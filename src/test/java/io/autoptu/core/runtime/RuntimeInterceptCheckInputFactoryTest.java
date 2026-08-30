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
    void derivesDistanceSkillsJustifiedCoachingAndTerrainFromServerOwnedState() {
        RuntimeCombatantState interceptor = combatant("interceptor", List.of("Justified [Errata]"));
        interceptor.temporaryEffects().add("coaching_intercept");
        BattleRuntimeState state = state(interceptor, "Forest");
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Naturewalk (Forest)"),
                4,
                "trainer-a",
                Map.of("Acrobatics", 3, "Athletics", 7),
                List.of("Survivalist"),
                List.of()
        );

        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state,
                "interceptor",
                content,
                new GridCoord(6, 1)
        );

        assertEquals(5, input.distance());
        assertEquals(3, input.acrobaticsRank());
        assertEquals(7, input.athleticsRank());
        assertEquals(4, input.justifiedBonus());
        assertEquals(2, input.terrainBonus());
        assertTrue(input.coachingAutomaticSuccess());
    }

    @Test
    void overlappingInterceptAnchorUsesPythonMinimumDistanceOne() {
        RuntimeCombatantState interceptor = combatant("interceptor");
        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor),
                "interceptor",
                CombatantRuleContent.empty(),
                interceptor.position()
        );

        assertEquals(1, input.distance());
    }

    @Test
    void speciesNaturewalkLabelAlsoFeedsTerrainBonus() {
        RuntimeCombatantState interceptor = combatant("interceptor");
        CombatantRuleContent content = new CombatantRuleContent(
                List.of(),
                4,
                "trainer-a",
                Map.of(),
                List.of("Survivalist"),
                List.of("Grassland")
        );

        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor, "Grassland"),
                "interceptor",
                content,
                new GridCoord(3, 1)
        );

        assertEquals(2, input.terrainBonus());
        assertEquals(List.of("Grassland"), content.effectiveNaturewalkLabels());
    }

    @Test
    void naturewalkCapabilityLabelsFollowPythonOrderAndCaseInsensitiveDeduplication() {
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Naturewalk (Forest)", "Naturewalk Tundra", "Naturewalk (grassland)"),
                4,
                "trainer-a",
                Map.of(),
                List.of(),
                List.of("Grassland")
        );

        assertEquals(
                List.of("Grassland", "Forest", "Tundra"),
                content.effectiveNaturewalkLabels()
        );
    }

    @Test
    void missingSurvivalistOrTerrainMatchDoesNotAddTerrainBonus() {
        RuntimeCombatantState interceptor = combatant("interceptor");
        CombatantRuleContent withoutSurvivalist = new CombatantRuleContent(
                List.of("Naturewalk (Forest)"),
                4,
                "trainer-a",
                Map.of(),
                List.of(),
                List.of()
        );
        CombatantRuleContent mismatched = new CombatantRuleContent(
                List.of("Naturewalk (Tundra)"),
                4,
                "trainer-a",
                Map.of(),
                List.of("Survivalist"),
                List.of()
        );

        assertEquals(0, RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor, "Forest"), "interceptor", withoutSurvivalist, new GridCoord(3, 1)
        ).terrainBonus());
        assertEquals(0, RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor, "Forest"), "interceptor", mismatched, new GridCoord(3, 1)
        ).terrainBonus());
    }

    @Test
    void absentJustifiedAndCoachingDoNotAddBonuses() {
        RuntimeCombatantState interceptor = combatant("interceptor");
        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor),
                "interceptor",
                CombatantRuleContent.empty(),
                new GridCoord(3, 1)
        );

        assertFalse(input.coachingAutomaticSuccess());
        assertEquals(0, input.justifiedBonus());
        assertEquals(0, input.terrainBonus());
        assertEquals(0, input.acrobaticsRank());
        assertEquals(0, input.athleticsRank());
    }

    @Test
    void similarlyNamedAbilityDoesNotGrantErrataBonus() {
        RuntimeCombatantState interceptor = combatant("interceptor", List.of("Justified"));
        RuntimeInterceptCheckApplication.Input input = RuntimeInterceptCheckInputFactory.fromState(
                state(interceptor),
                "interceptor",
                CombatantRuleContent.empty(),
                new GridCoord(3, 1)
        );

        assertEquals(0, input.justifiedBonus());
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
                        new GridCoord(2, 1)
                )
        );

        Method method = RuntimeInterceptCheckInputFactory.class.getDeclaredMethod(
                "fromState",
                BattleRuntimeState.class,
                String.class,
                CombatantRuleContent.class,
                GridCoord.class
        );
        assertFalse(Modifier.isPublic(RuntimeInterceptCheckInputFactory.class.getModifiers()));
        assertFalse(Modifier.isPublic(method.getModifiers()));
    }

    private static RuntimeCombatantState combatant(String id) {
        return combatant(id, List.of());
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 6),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }

    private static BattleRuntimeState state(RuntimeCombatantState interceptor) {
        return state(interceptor, "");
    }

    private static BattleRuntimeState state(RuntimeCombatantState interceptor, String terrain) {
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(interceptor)
        );
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState("", terrain, Set.of(), Map.of()));
        return state;
    }
}