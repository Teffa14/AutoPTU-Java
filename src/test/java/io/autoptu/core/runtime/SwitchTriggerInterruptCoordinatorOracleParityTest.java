package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.BuiltinSwitchTriggerPlanners;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.SwitchTriggerDecisionPlan;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwitchTriggerInterruptCoordinatorOracleParityTest {
    @Test
    void rejectedInterruptStopsBeforeApSwitchEffectsOrEvents() throws IOException {
        List<String> fixture = fixture();
        assertTrue(fixture.contains(
                "TRIGGER_RESPONSE\tfalsy=decline\ttruthy_non_dict=accept_default\tdict_accept_default=True\tdict_accept_false=decline"
        ));

        GridCoord position = new GridCoord(3, 4);
        RuntimeCombatantState actor = combatant("actor", position, 20);
        RuntimeCombatantState bench = combatant("bench", new GridCoord(0, 0), 20);
        BattleRuntimeState state = battle(actor, bench);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");

        SwitchTriggerDecisionPlan plan = plan(state);
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(Map.of("actor", position));
        ArrayList<BattleEvent> events = new ArrayList<>();

        SwitchTriggerInterruptCoordinator.Outcome outcome = SwitchTriggerInterruptCoordinator.resolveAndExecute(
                state,
                presence,
                LifecycleHookRegistry.builder().build(),
                events::add,
                plan,
                SwitchTriggerInterruptResponseResolver.Response.mapping(false, "bench")
        );

        assertFalse(outcome.accepted());
        assertEquals("", outcome.resolution().replacementId());
        assertNull(outcome.executionResult());
        assertEquals(2, trainer.ap());
        assertTrue(state.isActive("actor"));
        assertFalse(state.isActive("bench"));
        assertTrue(presence.isOnField("actor"));
        assertFalse(presence.isOnField("bench"));
        assertFalse(bench.temporaryEffects().has("quick_switch_sent_out"));
        assertTrue(events.isEmpty());
    }

    @Test
    void acceptedInvalidChoiceFallsBackThenUsesCanonicalExecutorOrder() throws IOException {
        List<String> fixture = fixture();
        assertTrue(fixture.contains(
                "TRIGGER_CHOICE\tdefault=first_replacement\tfield=choice\tlegal_requested=selected\tinvalid_requested=default_first"
        ));
        assertTrue(fixture.contains(
                "TRIGGER_ORDER\tconsume_ap\tapply_switch\tquick_switch_sent_out\ttrainer_feature_event"
        ));

        GridCoord position = new GridCoord(3, 4);
        RuntimeCombatantState actor = combatant("actor", position, 20);
        RuntimeCombatantState bench = combatant("bench", new GridCoord(0, 0), 20);
        BattleRuntimeState state = battle(actor, bench);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");
        seedInitiative(state);

        SwitchTriggerDecisionPlan plan = plan(state);
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(Map.of("actor", position));
        ArrayList<BattleEvent> events = new ArrayList<>();

        SwitchTriggerInterruptCoordinator.Outcome outcome = SwitchTriggerInterruptCoordinator.resolveAndExecute(
                state,
                presence,
                LifecycleHookRegistry.builder().build(),
                events::add,
                plan,
                SwitchTriggerInterruptResponseResolver.Response.mapping(true, "not-a-candidate")
        );

        assertTrue(outcome.accepted());
        assertEquals("bench", outcome.resolution().replacementId());
        assertEquals("bench", outcome.executionResult().replacementId());
        assertEquals(0, trainer.ap());
        assertFalse(state.isActive("actor"));
        assertTrue(state.isActive("bench"));
        assertFalse(presence.isOnField("actor"));
        assertTrue(presence.isOnField("bench"));
        assertTrue(bench.temporaryEffects().has("quick_switch_sent_out"));
        assertEquals(List.of(
                SwitchTriggerDecisionExecutor.Stage.SPEND_AP,
                SwitchTriggerDecisionExecutor.Stage.APPLY_SWITCH,
                SwitchTriggerDecisionExecutor.Stage.ADD_SENT_OUT_EFFECT,
                SwitchTriggerDecisionExecutor.Stage.EMIT_TRAINER_FEATURE_EVENT
        ), outcome.executionResult().stages());
        assertEquals(1, events.stream().filter(TrainerFeatureEvent.class::isInstance).count());
    }

    private static SwitchTriggerDecisionPlan plan(BattleRuntimeState state) {
        return BuiltinSwitchTriggerPlanners.paritySafe().plans(
                RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                        "actor"
                )
        ).getFirst();
    }

    private static void seedInitiative(BattleRuntimeState state) {
        state.initiativeProgress().replaceDetailedOrderFromLifecycle(List.of(
                new InitiativeEntry("actor", "trainer", 10, 0, 10, 20)
        ));
        state.initiativeProgress().setCursorFromLifecycle(0);
    }

    private static BattleRuntimeState battle(RuntimeCombatantState actor, RuntimeCombatantState bench) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(actor, bench),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "actor", CombatantAffiliationState.active("red"),
                        "bench", new CombatantAffiliationState("red", false)
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position, int hp) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 5),
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
