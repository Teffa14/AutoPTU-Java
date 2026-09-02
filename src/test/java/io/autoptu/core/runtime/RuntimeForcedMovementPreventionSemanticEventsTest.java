package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ForcedMovementPreventionResolution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeForcedMovementPreventionSemanticEventsTest {
    @Test
    void trainerFeaturePreventionMapsToPinnedPythonEventPayload() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                29,
                100,
                new ActionBudget()
        );
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Wallclimber"), null, "trainer-7", Map.of(),
                List.of("Insectoid Utility"), List.of()
        );
        ForcedMovementPreventionResolution.Prevention prevention = new ForcedMovementPreventionResolution.Prevention(
                ForcedMovementPreventionResolution.SourceKind.TRAINER_FEATURE,
                "Insectoid Utility"
        );

        List<BattleEvent> events = RuntimeForcedMovementPreventionSemanticEvents.resolve(
                choice(), target, content, prevention
        );

        assertEquals(1, events.size());
        TrainerFeatureEvent event = (TrainerFeatureEvent) events.getFirst();
        assertEquals("enemy", event.actorId());
        assertEquals("Insectoid Utility", event.feature());
        assertEquals("forced_movement_block", event.effect());
        assertEquals("actor", event.details().get("target"));
        assertEquals("trainer-7", event.trainer());
        assertEquals("Insectoid Utility's Wallclimber upgrade prevents push effects.", event.description());
        assertEquals(29, event.targetHp());
    }

    @Test
    void unresolvedOrNonTrainerFeaturePreventionDoesNotInventEvents() {
        RuntimeCombatantState target = new RuntimeCombatantState(
                "enemy",
                MovementProfile.walking(new GridCoord(2, 1), 3),
                29,
                100,
                new ActionBudget()
        );
        CombatantRuleContent content = CombatantRuleContent.empty();

        assertTrue(RuntimeForcedMovementPreventionSemanticEvents.resolve(
                choice(), target, content, ForcedMovementPreventionResolution.Prevention.none()
        ).isEmpty());
        assertTrue(RuntimeForcedMovementPreventionSemanticEvents.resolve(
                choice(), target, content,
                new ForcedMovementPreventionResolution.Prevention(
                        ForcedMovementPreventionResolution.SourceKind.STATUS, "Ingrain"
                )
        ).isEmpty());
    }

    private static MoveChoice choice() {
        return new MoveChoice(
                "actor",
                "ram",
                ChoiceTargetMode.COMBATANT,
                "enemy",
                new GridCoord(2, 1),
                ActionType.STANDARD
        );
    }
}
