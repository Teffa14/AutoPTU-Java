package io.autoptu.core.runtime;

import io.autoptu.core.hook.SwitchTriggerDecisionPlan;
import io.autoptu.core.hook.SwitchTriggerPlannerRegistry;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeSwitchTriggerPlanningContextFactoryTest {
    @Test
    void projectsCanonicalTrainerApFeaturesAndOrderedLegalReplacements() {
        RuntimeCombatantState actor = combatant("actor", 20);
        RuntimeCombatantState benchA = combatant("bench-a", 10);
        RuntimeCombatantState benchB = combatant("bench-b", 8);
        RuntimeCombatantState faintedBench = combatant("fainted-bench", 0);
        RuntimeCombatantState opponentBench = combatant("opponent-bench", 12);

        BattleRuntimeState state = battle(
                List.of(actor, benchA, benchB, faintedBench, opponentBench),
                Map.of(
                        "actor", new CombatantAffiliationState("red", true),
                        "bench-a", new CombatantAffiliationState("red", false),
                        "bench-b", new CombatantAffiliationState("red", false),
                        "fainted-bench", new CombatantAffiliationState("red", false),
                        "opponent-bench", new CombatantAffiliationState("blue", false)
                )
        );
        TrainerRuntimeState red = new TrainerRuntimeState("red-trainer", List.of("Quick Switch"), 3);
        TrainerRuntimeState blue = new TrainerRuntimeState("blue-trainer", List.of(), 4);
        state.putTrainer(red);
        state.putTrainer(blue);
        state.bindController("actor", "red-trainer");
        state.bindController("bench-a", "red-trainer");
        state.bindController("bench-b", "red-trainer");
        state.bindController("fainted-bench", "red-trainer");
        state.bindController("opponent-bench", "blue-trainer");

        SwitchTriggerPlannerRegistry.PlanningContext context =
                RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                        "actor"
                );

        assertEquals("actor", context.actorId());
        assertTrue(context.actorActive());
        assertFalse(context.actorFainted());
        assertEquals(3, context.availableAp());
        assertEquals(List.of("bench-a", "bench-b"), context.replacementIds());
        assertEquals(Set.of("quick switch"), context.trainerFeatures());
        assertFalse(context.triggerAlreadyHandled());
    }

    @Test
    void readsAllyFaintDedupeFromTriggerTargetTemporaryEffects() {
        RuntimeCombatantState actor = combatant("actor", 20);
        RuntimeCombatantState replacement = combatant("replacement", 10);
        RuntimeCombatantState faintedAlly = combatant("fainted-ally", 0);
        faintedAlly.temporaryEffects().add("quick_switch_faint_handled");

        BattleRuntimeState state = battle(
                List.of(actor, replacement, faintedAlly),
                Map.of(
                        "actor", new CombatantAffiliationState("red", true),
                        "replacement", new CombatantAffiliationState("red", false),
                        "fainted-ally", new CombatantAffiliationState("red", false)
                )
        );
        state.putTrainer(new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2));
        state.bindController("actor", "trainer");
        state.bindController("replacement", "trainer");
        state.bindController("fainted-ally", "trainer");

        SwitchTriggerPlannerRegistry.PlanningContext context =
                RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                        "actor",
                        "fainted-ally",
                        "quick_switch_faint_handled"
                );

        assertTrue(context.triggerAlreadyHandled());
        assertEquals(List.of("replacement"), context.replacementIds());
    }

    private static BattleRuntimeState battle(
            List<RuntimeCombatantState> combatants,
            Map<String, CombatantAffiliationState> affiliations
    ) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                combatants,
                Map.of(),
                Map.of(),
                Map.of(),
                affiliations
        );
    }

    private static RuntimeCombatantState combatant(String id, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(0, 0), 5),
                hp,
                20,
                new ActionBudget()
        );
    }
}
