package io.autoptu.core.runtime;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrainerInitiativeTurnOracleParityTest {
    @Test
    void pinnedPythonRecognizesTrainerInitiativeEntriesFromTrainerRegistry() throws IOException {
        String oraclePath = System.getProperty("autoptu.initiative.turn.oracle");
        if (oraclePath == null || oraclePath.isBlank()) return;

        Map<String, String> fixtures = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            fixtures.put(parts[0], parts[1]);
        }

        assertEquals("1", fixtures.get("recognizes_trainer_entries"));
        assertEquals("1", fixtures.get("reads_trainer_registry"));
        assertEquals("1", fixtures.get("resets_selected_actor_actions"));
        assertEquals("1", fixtures.get("sets_start_phase"));
        assertEquals("1", fixtures.get("logs_turn_start"));
    }

    @Test
    void trainerSlotUsesServerTrainerStateAndResetsActionsBeforeTurnStart() {
        RuntimeCombatantState pokemon = new RuntimeCombatantState(
                "pokemon",
                MovementProfile.walking(new GridCoord(1, 1), 2),
                20,
                20,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(pokemon)
        );
        TrainerRuntimeState trainer = new TrainerRuntimeState("trainer", List.of(), 5);
        trainer.actionBudget().markAction(ActionType.STANDARD, "stale");
        state.putTrainer(trainer);
        state.initiativeProgress().replaceOrderFromLifecycle(List.of("trainer", "pokemon"));

        BattleRoundController controller = new BattleRoundController(state, 4);
        InitiativeTurnAdvanceResult result = controller.advanceInitiativeTurn();

        assertEquals("trainer", result.actorId());
        assertEquals("trainer", controller.turnState().currentActorId());
        assertEquals(TurnPhase.START, controller.turnState().phase());
        assertTrue(trainer.actionBudget().hasActionAvailable(ActionType.STANDARD));
        TurnStartedEvent turnStart = assertInstanceOf(TurnStartedEvent.class, result.events().getFirst());
        assertEquals("trainer", turnStart.actorId());
        assertEquals(4, turnStart.round());
        assertEquals(0, turnStart.initiativeIndex());
    }
}
