package io.autoptu.core.runtime;

import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStatusSkipFeatureTest {
    @Test
    void supremeConcentrationBypassesSkipFromCanonicalFeatureState() {
        RuntimeCombatantState actor = actor();
        BattleRuntimeState state = state(
                actor,
                new StatusSkipFeatureState("Supreme Concentration", "Thunderbolt", false)
        );

        AppliedActionResult result = BattleRuntime.applyStatusSkip(
                state, "actor", "Flinch", TurnPhase.START, "failed_check"
        );

        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        TrainerFeatureEvent event = (TrainerFeatureEvent) result.events().getFirst();
        assertEquals("Signature Technique", event.feature());
        assertEquals("supreme_concentration", event.effect());
        assertEquals("Thunderbolt", event.move());
        assertEquals("Flinch", event.status());
        assertEquals(20, event.targetHp());
    }

    @Test
    void duelistsManualBypassesCoveredVolatileStatus() {
        RuntimeCombatantState actor = actor();
        BattleRuntimeState state = state(
                actor,
                new StatusSkipFeatureState("", "", true)
        );

        AppliedActionResult result = BattleRuntime.applyStatusSkip(
                state, "actor", "Confused", TurnPhase.START, "failed_check"
        );

        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertTrue(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        TrainerFeatureEvent event = (TrainerFeatureEvent) result.events().getFirst();
        assertEquals("Duelist's Manual", event.feature());
        assertEquals("ignore_status_skip", event.effect());
    }

    @Test
    void uncoveredStatusStillConsumesBaseTurnActions() {
        RuntimeCombatantState actor = actor();
        BattleRuntimeState state = state(
                actor,
                new StatusSkipFeatureState("Supreme Concentration", "Thunderbolt", false)
        );

        AppliedActionResult result = BattleRuntime.applyStatusSkip(
                state, "actor", "Sleep", TurnPhase.START, "failed_check"
        );

        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.STANDARD));
        assertFalse(actor.actionBudget().hasActionAvailable(ActionType.SHIFT));
        assertTrue(result.events().getFirst() instanceof StatusSkipEvent);
    }

    private static RuntimeCombatantState actor() {
        return new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 4),
                20,
                20,
                new ActionBudget()
        );
    }

    private static BattleRuntimeState state(
            RuntimeCombatantState actor,
            StatusSkipFeatureState featureState
    ) {
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor),
                Map.of(),
                Map.of("actor", featureState)
        );
    }
}
