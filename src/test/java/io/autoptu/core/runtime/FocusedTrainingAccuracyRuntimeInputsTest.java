package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FocusedTrainingAccuracyRuntimeInputsTest {
    @Test
    void defaultsToOneWithoutDuelistFeature() {
        Fixture fixture = fixture(List.of(), List.of());
        fixture.attacker().temporaryEffects().add("focused_training");

        assertEquals(1, FocusedTrainingAccuracyRuntimeInputs.resolve(
                fixture.state(), "attacker", "defender"));
    }

    @Test
    void derivesTaggedDefenderAndMomentumAcrossSameControllerCombatants() {
        Fixture fixture = fixture(List.of("Duelist"), List.of());
        fixture.attacker().temporaryEffects().add("focused_training");
        fixture.support().temporaryEffects().add("duelist_tag", Map.of(
                "tagged", true,
                "target_id", "defender",
                "momentum", 5
        ));

        assertEquals(3, FocusedTrainingAccuracyRuntimeInputs.resolve(
                fixture.state(), "attacker", "defender"));
    }

    @Test
    void ignoresTagsOwnedByAnotherController() {
        Fixture fixture = fixture(List.of("Duelist"), List.of("Duelist"));
        fixture.attacker().temporaryEffects().add("focused_training");
        fixture.foreign().temporaryEffects().add("duelist_tag", Map.of(
                "tagged", true,
                "target_id", "defender",
                "momentum", 99
        ));

        assertEquals(1, FocusedTrainingAccuracyRuntimeInputs.resolve(
                fixture.state(), "attacker", "defender"));
    }

    @Test
    void usesMaximumControllerMomentumEvenWhenHigherEntryIsNotTheTaggedTarget() {
        Fixture fixture = fixture(List.of("Duelist"), List.of());
        fixture.attacker().temporaryEffects().add("focused_training");
        fixture.attacker().temporaryEffects().add("duelist_tag", Map.of(
                "tagged", true,
                "target", "defender",
                "momentum", 3
        ));
        fixture.support().temporaryEffects().add("duelist_tag", Map.of(
                "tagged", false,
                "target_id", "other",
                "momentum", 7
        ));
        fixture.support().temporaryEffects().add("duelist_tag", Map.of(
                "tagged", true,
                "target_id", "other",
                "momentum", "invalid"
        ));

        assertEquals(4, FocusedTrainingAccuracyRuntimeInputs.resolve(
                fixture.state(), "attacker", "defender"));
    }

    @Test
    void fallsBackToHolderIdentityWhenTagHasNoExplicitTarget() {
        Fixture fixture = fixture(List.of("Duelist"), List.of());
        fixture.attacker().temporaryEffects().add("focused_training");
        fixture.support().temporaryEffects().add("duelist_tag", Map.of(
                "tagged", true,
                "momentum", 4
        ));

        assertEquals(2, FocusedTrainingAccuracyRuntimeInputs.resolve(
                fixture.state(), "attacker", "support"));
    }

    private static Fixture fixture(List<String> primaryFeatures, List<String> foreignFeatures) {
        RuntimeCombatantState attacker = combatant("attacker", 0);
        RuntimeCombatantState support = combatant("support", 1);
        RuntimeCombatantState defender = combatant("defender", 4);
        RuntimeCombatantState foreign = combatant("foreign", 5);

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                List.of(attacker, support, defender, foreign)
        );
        state.putTrainer(new TrainerRuntimeState("trainer-a", primaryFeatures, 0));
        state.putTrainer(new TrainerRuntimeState("trainer-b", foreignFeatures, 0));
        state.bindController("attacker", "trainer-a");
        state.bindController("support", "trainer-a");
        state.bindController("defender", "trainer-b");
        state.bindController("foreign", "trainer-b");
        return new Fixture(state, attacker, support, defender, foreign);
    }

    private static RuntimeCombatantState combatant(String id, int x) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, 0), 5),
                100,
                100,
                new ActionBudget()
        );
    }

    private record Fixture(
            BattleRuntimeState state,
            RuntimeCombatantState attacker,
            RuntimeCombatantState support,
            RuntimeCombatantState defender,
            RuntimeCombatantState foreign
    ) { }
}
