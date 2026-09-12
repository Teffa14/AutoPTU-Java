package io.autoptu.core.runtime;

import io.autoptu.core.hook.SwitchTriggerDecisionPlan;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeSwitchTriggerDispatcherOracleParityTest {
    @Test
    void armsAllyFaintGuardBeforePlanningWithoutSuppressingFirstDecision() throws IOException {
        assertTrue(fixture().contains(
                "FAINT_GUARD_ORDER\tcheck_guard\tarm_guard\tdispatch_quick_switch"
        ));
        assertTrue(fixture().contains(
                "FAINT_GUARD\tquick_switch_faint_handled\tround=current\texpires=current"
        ));

        RuntimeCombatantState actor = combatant("actor", 20);
        RuntimeCombatantState bench = combatant("bench", 20);
        RuntimeCombatantState faintedAlly = combatant("fainted", 0);
        BattleRuntimeState state = battle(actor, bench, faintedAlly);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");
        state.bindController("fainted", "trainer");

        RuntimeSwitchTriggerDispatcher.DispatchResult result = RuntimeSwitchTriggerDispatcher.paritySafe().dispatch(
                state,
                SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                "actor",
                "fainted"
        );

        assertEquals(1, result.decisions().size());
        SwitchTriggerDecisionPlan plan = result.decisions().getFirst();
        assertEquals(SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT, plan.trigger());
        assertEquals("actor", plan.actorId());
        assertEquals(List.of("bench"), plan.replacementIds());
        assertEquals("quick_switch_faint_handled", plan.dedupeEffectKey());

        RuntimeSwitchTriggerDispatcher.GuardObservation guard = result.guards().getFirst();
        assertFalse(guard.alreadyHandled());
        assertTrue(guard.armed());
        TemporaryEffectEntry entry = faintedAlly.temporaryEffects()
                .getAll("quick_switch_faint_handled")
                .getFirst();
        assertEquals(Map.of("round", state.currentRound(), "expires_round", state.currentRound()), entry.payload());
    }

    @Test
    void suppressesRepeatedAllyFaintDispatchFromPreexistingGuard() {
        RuntimeCombatantState actor = combatant("actor", 20);
        RuntimeCombatantState bench = combatant("bench", 20);
        RuntimeCombatantState faintedAlly = combatant("fainted", 0);
        BattleRuntimeState state = battle(actor, bench, faintedAlly);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");
        state.bindController("fainted", "trainer");
        faintedAlly.temporaryEffects().add(
                "quick_switch_faint_handled",
                Map.of("round", state.currentRound(), "expires_round", state.currentRound())
        );

        RuntimeSwitchTriggerDispatcher.DispatchResult result = RuntimeSwitchTriggerDispatcher.paritySafe().dispatch(
                state,
                SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                "actor",
                "fainted"
        );

        assertTrue(result.decisions().isEmpty());
        assertEquals(1, faintedAlly.temporaryEffects().count("quick_switch_faint_handled"));
        assertTrue(result.guards().getFirst().alreadyHandled());
        assertFalse(result.guards().getFirst().armed());
    }

    @Test
    void armsFaintGuardEvenWhenQuickSwitchCannotProduceDecision() {
        RuntimeCombatantState actor = combatant("actor", 20);
        RuntimeCombatantState bench = combatant("bench", 20);
        RuntimeCombatantState faintedAlly = combatant("fainted", 0);
        BattleRuntimeState state = battle(actor, bench, faintedAlly);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of(), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");
        state.bindController("fainted", "trainer");

        RuntimeSwitchTriggerDispatcher.DispatchResult result = RuntimeSwitchTriggerDispatcher.paritySafe().dispatch(
                state,
                SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                "actor",
                "fainted"
        );

        assertTrue(result.decisions().isEmpty());
        assertTrue(faintedAlly.temporaryEffects().has("quick_switch_faint_handled"));
        assertTrue(result.guards().getFirst().armed());
    }

    @Test
    void opponentSendOutPlansWithoutMutatingFaintGuardState() throws IOException {
        assertTrue(fixture().contains("TRIGGER_SOURCE\topponent_send_out\tally_faint"));

        RuntimeCombatantState actor = combatant("actor", 20);
        RuntimeCombatantState bench = combatant("bench", 20);
        RuntimeCombatantState sentOut = combatant("sent_out", 20);
        BattleRuntimeState state = battle(actor, bench, sentOut);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");

        RuntimeSwitchTriggerDispatcher.DispatchResult result = RuntimeSwitchTriggerDispatcher.paritySafe().dispatch(
                state,
                SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                "actor",
                "sent_out"
        );

        assertEquals(1, result.decisions().size());
        assertEquals(SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT, result.decisions().getFirst().trigger());
        assertTrue(result.guards().isEmpty());
        assertFalse(sentOut.temporaryEffects().has("quick_switch_faint_handled"));
    }

    private static BattleRuntimeState battle(RuntimeCombatantState actor, RuntimeCombatantState bench, RuntimeCombatantState target) {
        LinkedHashMap<String, CombatantAffiliationState> affiliations = new LinkedHashMap<>();
        affiliations.put("actor", CombatantAffiliationState.active("red"));
        affiliations.put("bench", new CombatantAffiliationState("red", false));
        affiliations.put(
                target.combatantId(),
                target.combatantId().equals("sent_out")
                        ? CombatantAffiliationState.active("blue")
                        : new CombatantAffiliationState("red", false)
        );
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(actor, bench, target),
                Map.of(),
                Map.of(),
                Map.of(),
                affiliations
        );
    }

    private static RuntimeCombatantState combatant(String id, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 5),
                hp,
                20,
                new ActionBudget(),
                new CombatantStatProfile(Map.of(), Map.of(), Map.of(), Set.of())
        );
    }

    private static List<String> fixture() throws IOException {
        Path fixture = Path.of("build/oracle/quick-switch-entry-contract.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        return Files.readAllLines(fixture);
    }
}
