package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoundStartAbilityDispatchPlanTest {
    @Test
    void preservesPythonFamilyAndCombatantInsertionOrder() {
        List<RoundStartAbilityDispatchPlan.Invocation> actual = RoundStartAbilityDispatchPlan.plan(
                " Rain ",
                List.of("air-two", "air-one"),
                List.of(
                        new RoundStartAbilityDispatchPlan.Combatant("actor-b", true, false),
                        new RoundStartAbilityDispatchPlan.Combatant("fainted", true, true),
                        new RoundStartAbilityDispatchPlan.Combatant("bench", false, false),
                        new RoundStartAbilityDispatchPlan.Combatant("actor-a", true, false)
                )
        );

        assertEquals(List.of(
                new RoundStartAbilityDispatchPlan.Invocation("air_lock", RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER, "air-two"),
                new RoundStartAbilityDispatchPlan.Invocation("air_lock", RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER, "air-one"),
                new RoundStartAbilityDispatchPlan.Invocation("arena_trap", RoundStartAbilityDispatchPlan.Scope.GLOBAL, ""),
                new RoundStartAbilityDispatchPlan.Invocation("intimidate", RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR, "actor-b"),
                new RoundStartAbilityDispatchPlan.Invocation("impostor", RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR, "actor-b"),
                new RoundStartAbilityDispatchPlan.Invocation("intimidate", RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR, "actor-a"),
                new RoundStartAbilityDispatchPlan.Invocation("impostor", RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR, "actor-a")
        ), actual);
    }

    @Test
    void clearAndNormalWeatherSuppressOnlyAirLockInvocations() {
        List<RoundStartAbilityDispatchPlan.Combatant> combatants = List.of(
                new RoundStartAbilityDispatchPlan.Combatant("actor", true, false)
        );
        List<RoundStartAbilityDispatchPlan.Invocation> expected = List.of(
                new RoundStartAbilityDispatchPlan.Invocation("arena_trap", RoundStartAbilityDispatchPlan.Scope.GLOBAL, ""),
                new RoundStartAbilityDispatchPlan.Invocation("intimidate", RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR, "actor"),
                new RoundStartAbilityDispatchPlan.Invocation("impostor", RoundStartAbilityDispatchPlan.Scope.ACTIVE_ACTOR, "actor")
        );

        assertEquals(expected, RoundStartAbilityDispatchPlan.plan(" CLEAR ", List.of("holder"), combatants));
        assertEquals(expected, RoundStartAbilityDispatchPlan.plan("normal", List.of("holder"), combatants));
    }

    @Test
    void blankWeatherStillProducesAirLockLikePinnedPython() {
        assertEquals(
                List.of(
                        new RoundStartAbilityDispatchPlan.Invocation("air_lock", RoundStartAbilityDispatchPlan.Scope.ABILITY_HOLDER, "holder"),
                        new RoundStartAbilityDispatchPlan.Invocation("arena_trap", RoundStartAbilityDispatchPlan.Scope.GLOBAL, "")
                ),
                RoundStartAbilityDispatchPlan.plan("", List.of("holder"), List.of())
        );
    }

    @Test
    void rejectsBlankHolderIds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RoundStartAbilityDispatchPlan.plan("rain", List.of(" "), List.of())
        );
    }
}
