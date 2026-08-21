package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerRuntimeStateTest {
    @Test
    void trainerFeaturesApAndInitiativeModifierAreServerOwned() {
        ArrayList<String> sourceFeatures = new ArrayList<>(List.of("Defense Mastery", "Attack Link"));
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer-1", sourceFeatures, 2, -3);
        sourceFeatures.clear();

        assertEquals(List.of("Defense Mastery", "Attack Link"), trainer.trainerFeatures());
        assertTrue(trainer.hasTrainerFeature("defense mastery"));
        assertTrue(trainer.hasTrainerFeature("ATTACK LINK"));
        assertEquals(-3, trainer.initiativeModifier());
        assertTrue(trainer.spendAp(1));
        assertEquals(1, trainer.ap());
        assertFalse(trainer.spendAp(2));
        assertEquals(1, trainer.ap());
        trainer.restoreAp(2);
        assertEquals(3, trainer.ap());
    }

    @Test
    void legacyConstructorUsesPythonDefaultInitiativeModifier() {
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer-1", List.of(), 1);
        assertEquals(0, trainer.initiativeModifier());
    }

    @Test
    void duplicateFeaturesFailClosed() {
        assertThrows(IllegalArgumentException.class, () ->
                new TrainerRuntimeState("trainer-1", List.of("Defense Mastery", "defense mastery"), 1)
        );
    }

    @Test
    void battleStateOwnsCombatantControllerBinding() {
        BattleRuntimeState state = state();
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer-1", List.of("Defense Mastery"), 1, 4);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer-1");

        assertEquals("trainer-1", state.controllerId("actor"));
        assertEquals(List.of("Defense Mastery"), state.trainerFeatures("actor"));
        assertEquals(trainer, state.requireTrainerForCombatant("actor"));
        assertEquals(4, state.requireTrainerForCombatant("actor").initiativeModifier());
        assertThrows(IllegalArgumentException.class, () -> state.bindController("actor", "missing"));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor", MovementProfile.walking(new GridCoord(1, 1), 1), 20, 20, new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()), List.of(actor)
        );
    }
}
