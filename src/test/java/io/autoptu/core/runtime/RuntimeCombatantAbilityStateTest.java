package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCombatantAbilityStateTest {
    @Test
    void abilityIdentitiesAreCopiedAndMatchedExactlyIgnoringCase() {
        ArrayList<String> abilities = new ArrayList<>();
        abilities.add("Mega Launcher [Errata]");
        RuntimeCombatantState combatant = combatant(abilities);

        abilities.clear();

        assertTrue(combatant.hasAbilityExact("mega launcher [errata]"));
        assertFalse(combatant.hasAbilityExact("Mega Launcher"));
    }

    private static RuntimeCombatantState combatant(List<String> abilities) {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
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
                List.of("Water"),
                List.of(),
                abilities
        );
    }
}
