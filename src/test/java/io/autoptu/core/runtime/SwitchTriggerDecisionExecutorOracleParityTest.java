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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwitchTriggerDecisionExecutorOracleParityTest {
    @Test
    void executesAcceptedQuickSwitchInPinnedOracleOrder() throws IOException {
        List<String> fixture = fixture();
        assertTrue(fixture.contains(
                "TRIGGER_ORDER\tconsume_ap\tapply_switch\tquick_switch_sent_out\ttrainer_feature_event"
        ));
        assertTrue(fixture.contains(
                "TRIGGER_TEMP\tquick_switch_sent_out\tround=current\texpires=current"
        ));
        assertTrue(fixture.contains(
                "TRIGGER_EVENT\ttype=trainer_feature\tfeature=Quick Switch\teffect=switch\ttrigger=propagated\tap_cost=2"
        ));

        GridCoord outgoingPosition = new GridCoord(3, 4);
        RuntimeCombatantState actor = combatant("actor", outgoingPosition, 20);
        RuntimeCombatantState replacement = combatant("bench", new GridCoord(0, 0), 20);
        BattleRuntimeState state = battle(actor, replacement);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 3);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");
        seedInitiative(state);

        SwitchTriggerDecisionPlan plan = BuiltinSwitchTriggerPlanners.paritySafe().plans(
                RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                        "actor"
                )
        ).getFirst();

        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(
                Map.of("actor", outgoingPosition)
        );
        ArrayList<BattleEvent> events = new ArrayList<>();

        SwitchTriggerDecisionExecutor.ExecutionResult result = SwitchTriggerDecisionExecutor.execute(
                state,
                presence,
                LifecycleHookRegistry.builder().build(),
                event -> {
                    events.add(event);
                    if (event instanceof TrainerFeatureEvent) {
                        assertEquals(1, trainer.ap());
                        assertFalse(state.isActive("actor"));
                        assertTrue(state.isActive("bench"));
                        assertTrue(replacement.temporaryEffects().has("quick_switch_sent_out"));
                    }
                },
                plan,
                "bench"
        );

        assertEquals(3, result.apBefore());
        assertEquals(1, result.apAfter());
        assertEquals(List.of(
                SwitchTriggerDecisionExecutor.Stage.SPEND_AP,
                SwitchTriggerDecisionExecutor.Stage.APPLY_SWITCH,
                SwitchTriggerDecisionExecutor.Stage.ADD_SENT_OUT_EFFECT,
                SwitchTriggerDecisionExecutor.Stage.EMIT_TRAINER_FEATURE_EVENT
        ), result.stages());
        assertEquals(outgoingPosition, presence.position("bench").orElseThrow());
        assertFalse(presence.isOnField("actor"));
        assertTrue(presence.isOnField("bench"));
        assertTrue(state.initiativeProgress().orderedActorIds().contains("bench"));

        TemporaryEffectEntry sentOut = replacement.temporaryEffects()
                .getAll("quick_switch_sent_out")
                .getFirst();
        assertEquals(Map.of("round", state.currentRound(), "expires_round", state.currentRound()), sentOut.payload());

        TrainerFeatureEvent featureEvent = (TrainerFeatureEvent) events.getLast();
        assertEquals("actor", featureEvent.actorId());
        assertEquals("Quick Switch", featureEvent.feature());
        assertEquals("switch", featureEvent.effect());
        assertEquals("opponent_send_out", featureEvent.details().get("trigger"));
        assertEquals(2, featureEvent.details().get("ap_cost"));
    }

    @Test
    void rejectsStaleReplacementBeforeSpendingApOrMutatingSwitchState() {
        GridCoord outgoingPosition = new GridCoord(2, 2);
        RuntimeCombatantState actor = combatant("actor", outgoingPosition, 20);
        RuntimeCombatantState replacement = combatant("bench", new GridCoord(0, 0), 20);
        BattleRuntimeState state = battle(actor, replacement);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");
        seedInitiative(state);

        SwitchTriggerDecisionPlan plan = BuiltinSwitchTriggerPlanners.paritySafe().plans(
                RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                        "actor"
                )
        ).getFirst();
        replacement.setHp(0);

        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(
                Map.of("actor", outgoingPosition)
        );
        assertThrows(IllegalArgumentException.class, () -> SwitchTriggerDecisionExecutor.execute(
                state,
                presence,
                LifecycleHookRegistry.builder().build(),
                event -> {},
                plan,
                "bench"
        ));

        assertEquals(2, trainer.ap());
        assertTrue(state.isActive("actor"));
        assertFalse(state.isActive("bench"));
        assertTrue(presence.isOnField("actor"));
        assertFalse(presence.isOnField("bench"));
        assertFalse(replacement.temporaryEffects().has("quick_switch_sent_out"));
    }

    @Test
    void rejectsMissingDetailedInitiativeBeforeSpendingApOrMutatingSwitchState() {
        GridCoord outgoingPosition = new GridCoord(5, 5);
        RuntimeCombatantState actor = combatant("actor", outgoingPosition, 20);
        RuntimeCombatantState replacement = combatant("bench", new GridCoord(0, 0), 20);
        BattleRuntimeState state = battle(actor, replacement);
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of("Quick Switch"), 2);
        state.putTrainer(trainer);
        state.bindController("actor", "trainer");
        state.bindController("bench", "trainer");

        SwitchTriggerDecisionPlan plan = BuiltinSwitchTriggerPlanners.paritySafe().plans(
                RuntimeSwitchTriggerPlanningContextFactory.fromState(
                        state,
                        SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                        "actor"
                )
        ).getFirst();
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(
                Map.of("actor", outgoingPosition)
        );

        assertThrows(IllegalArgumentException.class, () -> SwitchTriggerDecisionExecutor.execute(
                state,
                presence,
                LifecycleHookRegistry.builder().build(),
                event -> {},
                plan,
                "bench"
        ));

        assertEquals(2, trainer.ap());
        assertTrue(state.isActive("actor"));
        assertFalse(state.isActive("bench"));
        assertTrue(presence.isOnField("actor"));
        assertFalse(presence.isOnField("bench"));
        assertFalse(replacement.temporaryEffects().has("quick_switch_sent_out"));
    }

    private static void seedInitiative(BattleRuntimeState state) {
        state.initiativeProgress().replaceDetailedOrderFromLifecycle(List.of(
                new InitiativeEntry("actor", "trainer", 10, 0, 10, 20)
        ));
        state.initiativeProgress().setCursorFromLifecycle(0);
    }

    private static BattleRuntimeState battle(RuntimeCombatantState actor, RuntimeCombatantState replacement) {
        return new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(actor, replacement),
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
