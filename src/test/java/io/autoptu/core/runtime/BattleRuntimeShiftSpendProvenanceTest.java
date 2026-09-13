package io.autoptu.core.runtime;

import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionEconomyProfile;
import io.autoptu.core.rules.ActionSpendResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattleRuntimeShiftSpendProvenanceTest {
    @Test
    void baseShiftSpendSurvivesAuthoritativeRuntimeBoundary() {
        ActionBudget budget = new ActionBudget();
        BattleRuntimeState state = state(budget);

        AppliedActionResult result = BattleRuntime.applyAction(
                state,
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        );

        assertEquals(ActionSpendResult.Source.BASE, result.actionSpendResult().orElseThrow().source());
        assertEquals(new GridCoord(2, 1), state.requireCombatant("actor").position());
        assertEquals(1, result.events().size());
        assertInstanceOf(ShiftResolvedEvent.class, result.events().getFirst());
        assertEquals("shift_resolved|actor|1,1|2,1", result.events().getFirst().stableKey());
    }

    @Test
    void namedExtraShiftSpendSurvivesAuthoritativeRuntimeBoundary() {
        ActionBudget budget = new ActionBudget();
        assertTrue(budget.consume(ActionType.SHIFT, "non-movement-shift"));
        budget.grantExtra(ActionType.SHIFT, "feature:quick-shift", 1);
        BattleRuntimeState state = state(budget);

        AppliedActionResult result = BattleRuntime.applyAction(
                state,
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        );

        ActionSpendResult spend = result.actionSpendResult().orElseThrow();
        assertEquals(ActionSpendResult.Source.EXTRA, spend.source());
        assertEquals("feature:quick-shift", spend.extraGrantName().orElseThrow());
        assertEquals(0, budget.extraCount(ActionType.SHIFT));
        assertEquals(new GridCoord(2, 1), state.requireCombatant("actor").position());
        assertEquals("shift_resolved|actor|1,1|2,1", result.events().getFirst().stableKey());
    }

    @Test
    void kairosStandardConversionShiftSpendSurvivesAuthoritativeRuntimeBoundary() {
        ActionBudget budget = new ActionBudget(ActionEconomyProfile.KAIROS_2_1_25_1);
        assertTrue(budget.consume(ActionType.SHIFT, "non-movement-shift"));
        BattleRuntimeState state = state(budget);

        AppliedActionResult result = BattleRuntime.applyAction(
                state,
                new ShiftChoice("actor", new GridCoord(2, 1)),
                ignored -> true
        );

        assertEquals(ActionSpendResult.Source.STANDARD_CONVERSION,
                result.actionSpendResult().orElseThrow().source());
        assertEquals(ActionType.SHIFT, budget.standardConversion().orElseThrow());
        assertEquals(new GridCoord(2, 1), state.requireCombatant("actor").position());
        assertEquals("shift_resolved|actor|1,1|2,1", result.events().getFirst().stableKey());
    }

    private static BattleRuntimeState state(ActionBudget budget) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), 3),
                10,
                10,
                budget
        );
        return new BattleRuntimeState(
                new MovementGrid(6, 6, Set.of(), Map.of()),
                List.of(actor)
        );
    }
}
