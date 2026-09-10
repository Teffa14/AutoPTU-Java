package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatantSwitchExecutionPlanOracleParityTest {
    @Test
    void orderedSwitchTransactionMatchesPinnedPythonApplySwitchSource() throws IOException {
        Path fixture = Path.of("build/oracle/switch-execution-order.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        String line = Files.readString(fixture).strip();
        String expected = line.substring(line.indexOf('\t') + 1);

        RuntimeCombatantState outgoing = combatant("a-1", new GridCoord(4, 3));
        RuntimeCombatantState replacement = combatant("a-2", new GridCoord(0, 0));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(10, 10, Set.of(), Map.of()),
                List.of(outgoing, replacement),
                Map.of(), Map.of(), Map.of(),
                Map.of(
                        "a-1", CombatantAffiliationState.active("players"),
                        "a-2", new CombatantAffiliationState("players", false)
                )
        );
        state.syncCurrentRoundFromLifecycle(3);

        CombatantSwitchExecutionPlan plan = CombatantSwitchExecutionPlan.resolve(state, "a-1", "a-2");
        String actual = plan.stages().stream().map(Enum::name).collect(Collectors.joining(","));

        assertEquals(expected, actual);
        assertEquals(CombatantSwitchEntryStatePlan.JOINED_ROUND,
                plan.entryState().temporaryEffectMutations().get(0).effectName());
        assertEquals(CombatantSwitchEntryStatePlan.RELEASED_FROM_BALL,
                plan.entryState().temporaryEffectMutations().get(1).effectName());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 4), 20, 20, new ActionBudget());
    }
}
