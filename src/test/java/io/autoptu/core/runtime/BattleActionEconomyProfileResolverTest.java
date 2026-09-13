package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionEconomyProfile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BattleActionEconomyProfileResolverTest {
    @Test
    void emptyBattleDefaultsToPythonCompatibility() {
        assertEquals(
                ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY,
                BattleActionEconomyProfileResolver.resolve(List.of())
        );
    }

    @Test
    void homogeneousPythonBattleResolvesPythonCompatibility() {
        assertEquals(
                ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY,
                BattleActionEconomyProfileResolver.resolve(List.of(
                        combatant("one", ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY),
                        combatant("two", ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY)
                ))
        );
    }

    @Test
    void homogeneousKairosBattleResolvesKairosProfile() {
        assertEquals(
                ActionEconomyProfile.KAIROS_2_1_25_1,
                BattleActionEconomyProfileResolver.resolve(List.of(
                        combatant("one", ActionEconomyProfile.KAIROS_2_1_25_1),
                        combatant("two", ActionEconomyProfile.KAIROS_2_1_25_1)
                ))
        );
    }

    @Test
    void mixedProfilesAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                BattleActionEconomyProfileResolver.resolve(List.of(
                        combatant("one", ActionEconomyProfile.PYTHON_ORACLE_COMPATIBILITY),
                        combatant("two", ActionEconomyProfile.KAIROS_2_1_25_1)
                ))
        );
    }

    private static RuntimeCombatantState combatant(String id, ActionEconomyProfile profile) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(0, 0), 3),
                10,
                10,
                new ActionBudget(profile)
        );
    }
}
