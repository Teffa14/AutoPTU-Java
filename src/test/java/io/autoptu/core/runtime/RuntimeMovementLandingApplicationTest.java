package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMovementLandingApplicationTest {
    @Test
    void appliesStatusBeforeSemanticTriggerAndConsumesTrapAfterTrigger() {
        GridCoord landing = new GridCoord(3, 2);
        RuntimeCombatantState target = new RuntimeCombatantState(
                "target",
                MovementProfile.walking(landing, 5),
                42,
                60,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(8, 8, Set.of(), Map.of()),
                List.of(target),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("target", CombatantAffiliationState.active("blue"))
        );
        state.putTileTrapFromRuntime(
                landing,
                new TileEntryTrapResolution.TrapLayer(
                        "sticky_trap",
                        1,
                        "source",
                        "red",
                        Set.of("forest"),
                        "Sticky Trap"
                )
        );

        List<MovementLandingConsequenceExecutor.SemanticEvent> observed = new ArrayList<>();
        MovementLandingConsequenceExecutor.ExecutionResult result = RuntimeMovementLandingApplication.apply(
                state,
                "target",
                CombatantRuleContent.empty(),
                event -> {
                    assertTrue(state.hasStatus("target", "Slowed"));
                    assertFalse(state.tileTrapsAt(landing).isEmpty());
                    observed.add(event);
                }
        );

        assertEquals(1, observed.size());
        assertEquals(
                MovementLandingConsequenceExecutor.SemanticEventKind.TRAP_TRIGGER,
                observed.getFirst().kind()
        );
        assertTrue(state.hasStatus("target", "Slowed"));
        assertTrue(state.tileTrapsAt(landing).isEmpty());
        assertEquals(List.of("sticky_trap"), result.consumedTrapKeys());
        assertEquals(1, result.statusApplications().size());
    }

    @Test
    void naturewalkBlockLeavesStatusAndTrapUntouched() {
        GridCoord landing = new GridCoord(2, 1);
        RuntimeCombatantState target = new RuntimeCombatantState(
                "target",
                MovementProfile.walking(landing, 5),
                30,
                30,
                new ActionBudget()
        );
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(target)
        );
        state.putTileTrapFromRuntime(
                landing,
                new TileEntryTrapResolution.TrapLayer(
                        "snow_trap",
                        1,
                        "source",
                        "red",
                        Set.of("tundra"),
                        "Snow Trap"
                )
        );
        CombatantRuleContent content = new CombatantRuleContent(
                List.of("Naturewalk (Tundra)"),
                null,
                "",
                Map.of(),
                List.of(),
                List.of()
        );
        List<MovementLandingConsequenceExecutor.SemanticEvent> observed = new ArrayList<>();

        MovementLandingConsequenceExecutor.ExecutionResult result = RuntimeMovementLandingApplication.apply(
                state,
                "target",
                content,
                observed::add
        );

        assertEquals(1, observed.size());
        assertEquals(
                MovementLandingConsequenceExecutor.SemanticEventKind.TRAP_BLOCK,
                observed.getFirst().kind()
        );
        assertFalse(state.hasStatus("target", "Slowed"));
        assertEquals(1, state.tileTrapsAt(landing).size());
        assertTrue(result.consumedTrapKeys().isEmpty());
        assertTrue(result.statusApplications().isEmpty());
    }
}
