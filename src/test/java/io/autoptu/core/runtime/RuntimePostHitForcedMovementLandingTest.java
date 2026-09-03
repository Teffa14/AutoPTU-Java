package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.TerrainHazardEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePostHitForcedMovementLandingTest {
    @Test
    void successfulForcedMovementAppliesLandingConsequencesAndExposesResolvedHazardEvent() {
        RuntimeCombatantState source = combatant("source", 1, 1);
        RuntimeCombatantState target = combatant("target", 2, 1);
        MoveOption move = new MoveOption(
                "ram",
                new MoveSpec(
                        "Melee", "Melee", 1, 1, null, null, "Melee",
                        List.of("push 2"), "Push the target 2 meters."
                ),
                ActionType.STANDARD,
                true
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 4, Set.of(), Map.of()),
                List.of(source, target), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("source", List.of(move))
        );
        GridCoord landing = new GridCoord(4, 1);
        state.putTileTrapFromRuntime(
                landing,
                new TileEntryTrapResolution.TrapLayer(
                        "sticky_trap", 1, "trap-source", "red", Set.of("forest"), "Sticky Trap"
                )
        );
        MoveChoice choice = new MoveChoice(
                source.combatantId(), move.moveId(), ChoiceTargetMode.COMBATANT,
                target.combatantId(), target.position(), move.actionType()
        );

        RuntimePostHitForcedMovementApplication.SemanticResolution result =
                RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                        state, choice, true, BattleRuntimeDependencies.empty()
                );

        assertTrue(result.resolution().movement().isPresent());
        assertEquals(landing, target.position());
        assertTrue(state.hasStatus("target", "Slowed"));
        assertTrue(state.tileTrapsAt(landing).isEmpty());
        assertEquals(1, result.events().size());

        TerrainHazardEvent event = assertInstanceOf(TerrainHazardEvent.class, result.events().get(0));
        assertEquals("trigger", event.effect());
        assertEquals("target", event.actorId());
        assertEquals("sticky_trap", event.trapKey());
        assertEquals("Sticky Trap", event.trapName());
        assertEquals("trap-source", event.sourceId());
        assertEquals(20, event.targetHp());
        assertEquals(landing, event.coordinate());
        assertEquals(Set.of("forest"), event.terrains());
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 6),
                20,
                20,
                new ActionBudget()
        );
    }
}
