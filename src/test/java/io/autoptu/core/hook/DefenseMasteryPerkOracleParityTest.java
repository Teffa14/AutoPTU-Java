package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.runtime.BattleRoundController;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.TemporaryEffectEntry;
import io.autoptu.core.runtime.TrainerRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefenseMasteryPerkOracleParityTest {
    @Test
    void defaultLifecycleGrantsDamageReductionFromCanonicalTrainerFeature() {
        Fixture fixture = fixture(List.of("Defense Mastery"));

        List<BattleEvent> events = advanceToEnd(fixture.controller());

        List<TemporaryEffectEntry> reductions = fixture.actor().temporaryEffects().getAll("damage_reduction");
        assertEquals(1, reductions.size());
        assertEquals(5, reductions.getFirst().payload().get("amount"));
        assertEquals(4, reductions.getFirst().payload().get("expires_round"));
        assertEquals(false, reductions.getFirst().payload().get("consume"));
        assertEquals("Defense Mastery", reductions.getFirst().payload().get("source"));

        TrainerFeatureEvent event = events.stream()
                .filter(TrainerFeatureEvent.class::isInstance)
                .map(TrainerFeatureEvent.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("actor", event.actorId());
        assertEquals("trainer", event.trainer());
        assertEquals("Defense Mastery", event.feature());
        assertEquals("damage_reduction", event.effect());
        assertEquals(5, event.amount());
        assertEquals("end", event.phase());
        assertEquals(20, event.targetHp());
    }

    @Test
    void currentRoundShiftSuppressesDefenseMastery() {
        Fixture fixture = fixture(List.of("Defense Mastery"));
        fixture.actor().temporaryEffects().add("shifted_this_turn", Map.of("round", 3));

        List<BattleEvent> events = advanceToEnd(fixture.controller());

        assertTrue(fixture.actor().temporaryEffects().getAll("damage_reduction").isEmpty());
        assertTrue(events.stream().noneMatch(TrainerFeatureEvent.class::isInstance));
    }

    @Test
    void missingCanonicalFeatureFailsClosed() {
        Fixture fixture = fixture(List.of());

        List<BattleEvent> events = advanceToEnd(fixture.controller());

        assertTrue(fixture.actor().temporaryEffects().getAll("damage_reduction").isEmpty());
        assertTrue(events.stream().noneMatch(TrainerFeatureEvent.class::isInstance));
    }

    private static List<BattleEvent> advanceToEnd(BattleRoundController controller) {
        controller.beginTurn("actor");
        controller.advancePhase(); // START -> COMMAND
        controller.advancePhase(); // COMMAND -> ACTION
        return controller.advancePhase(); // ACTION -> END
    }

    private static Fixture fixture(List<String> trainerFeatures) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 2),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                List.of(actor)
        );
        state.putTrainer(new TrainerRuntimeState("trainer", trainerFeatures, 2));
        state.bindController("actor", "trainer");
        BattleRoundController controller = new BattleRoundController(state, 3);
        return new Fixture(actor, controller);
    }

    private record Fixture(RuntimeCombatantState actor, BattleRoundController controller) {}
}
