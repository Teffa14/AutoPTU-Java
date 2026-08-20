package io.autoptu.core.runtime;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.event.TurnStartedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitiativeTurnLifecycleOracleParityTest {
    @Test
    void pinnedPythonDeclaresExpectedInitiativeTurnContract() throws IOException {
        String oraclePath = System.getProperty("autoptu.initiative.turn.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        Map<String, String> fixtures = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            fixtures.put(parts[0], parts[1]);
        }

        assertEquals("1", fixtures.get("increments_cursor_before_selection"));
        assertEquals("1", fixtures.get("rollover_calls_start_round"));
        assertEquals("1", fixtures.get("reads_initiative_order"));
        assertEquals("1", fixtures.get("guards_active_state"));
        assertEquals("1", fixtures.get("guards_fainted_state"));
        assertEquals("1", fixtures.get("resets_selected_actor_actions"));
        assertEquals("1", fixtures.get("assigns_current_actor"));
        assertEquals("1", fixtures.get("sets_start_phase"));
        assertEquals("1", fixtures.get("logs_turn_start"));
        assertEquals("1", fixtures.get("runs_start_phase_effects"));
        assertEquals("1", fixtures.get("consumes_pending_status_skip"));
        assertEquals("1", fixtures.get("turn_start_precedes_start_effects"));
        assertEquals("1", fixtures.get("start_effects_precede_pending_skip"));
    }

    @Test
    void currentRoundInitiativeSkipsInvalidSlotsAndOpensAuthoritativeTurn() {
        RuntimeCombatantState fainted = combatant("fainted", 0);
        RuntimeCombatantState inactive = combatant("inactive", 20);
        RuntimeCombatantState actor = combatant("actor", 20);
        actor.actionBudget().markAction(ActionType.STANDARD, "stale prior-turn standard");
        actor.actionBudget().markAction(ActionType.SHIFT, "stale prior-turn shift");
        actor.actionBudget().grantExtra(ActionType.STANDARD);

        BattleRuntimeState state = state(
                List.of(fainted, inactive, actor),
                Map.of(
                        "fainted", CombatantAffiliationState.active("red"),
                        "inactive", new CombatantAffiliationState("red", false),
                        "actor", CombatantAffiliationState.active("blue")
                )
        );
        BattleRoundController controller = new BattleRoundController(state, 3);
        controller.replaceInitiativeOrder(List.of("missing", "fainted", "inactive", "actor"));

        InitiativeTurnAdvanceResult result = controller.advanceInitiativeTurn();

        assertTrue(result.hasActor());
        assertFalse(result.roundExhausted());
        assertEquals("actor", result.actorId());
        assertEquals(3, result.initiativeIndex());
        assertEquals(3, state.initiativeProgress().cursor());
        assertEquals("actor", controller.turnState().currentActorId());
        assertEquals(TurnPhase.START, controller.turnState().phase());
        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertEquals(1, actor.actionBudget().extraCount(ActionType.STANDARD));
        assertEquals(1, result.events().size());
        TurnStartedEvent event = (TurnStartedEvent) result.events().getFirst();
        assertEquals("actor", event.actorId());
        assertEquals(3, event.round());
        assertEquals(3, event.initiativeIndex());
        assertEquals(TurnPhase.START, event.phase());
    }

    @Test
    void startEffectsAndPendingSkipResolveBeforeDecisionWindow() {
        RuntimeCombatantState actor = combatant("actor", 20);
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor),
                Map.of("actor", Set.of("Flinch")),
                Map.of(),
                Map.of(),
                Map.of("actor", CombatantAffiliationState.active("blue"))
        );
        BattleRoundController controller = new BattleRoundController(state, 3);
        controller.replaceInitiativeOrder(List.of("actor"));

        InitiativeTurnAdvanceResult result = controller.advanceInitiativeTurn();

        assertTrue(result.hasActor());
        assertEquals(3, result.events().size());
        assertInstanceOf(TurnStartedEvent.class, result.events().get(0));
        RuleEffectEvent flinch = assertInstanceOf(RuleEffectEvent.class, result.events().get(1));
        assertEquals("status", flinch.sourceKind());
        assertEquals("flinch", flinch.sourceName());
        assertEquals("flinch", flinch.effect());
        StatusSkipEvent skip = assertInstanceOf(StatusSkipEvent.class, result.events().get(2));
        assertEquals("actor", skip.actorId());
        assertEquals(TurnPhase.START, skip.phase());
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
    }

    @Test
    void activeTurnMustEndBeforeInitiativeCanAdvanceAgain() {
        RuntimeCombatantState first = combatant("first", 20);
        RuntimeCombatantState second = combatant("second", 20);
        BattleRoundController controller = new BattleRoundController(state(List.of(first, second), Map.of()));
        controller.replaceInitiativeOrder(List.of("first", "second"));

        controller.advanceInitiativeTurn();
        assertThrows(IllegalStateException.class, controller::advanceInitiativeTurn);

        controller.endTurn();
        InitiativeTurnAdvanceResult secondTurn = controller.advanceInitiativeTurn();
        assertEquals("second", secondTurn.actorId());
        assertEquals(1, secondTurn.initiativeIndex());
    }

    @Test
    void exhaustingCurrentRoundDoesNotInventNewInitiativeRolls() {
        RuntimeCombatantState actor = combatant("actor", 20);
        BattleRoundController controller = new BattleRoundController(state(List.of(actor), Map.of()));
        controller.replaceInitiativeOrder(List.of("actor"));

        controller.advanceInitiativeTurn();
        controller.endTurn();
        InitiativeTurnAdvanceResult exhausted = controller.advanceInitiativeTurn();

        assertFalse(exhausted.hasActor());
        assertTrue(exhausted.roundExhausted());
        assertEquals(1, exhausted.initiativeIndex());
        assertEquals(1, controller.initiativeProgress().cursor());
        assertEquals(0, controller.round());
    }

    private static BattleRuntimeState state(
            List<RuntimeCombatantState> combatants,
            Map<String, CombatantAffiliationState> affiliations
    ) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
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
                MovementProfile.walking(new GridCoord(1, 1), 2),
                hp,
                20,
                new ActionBudget()
        );
    }
}
