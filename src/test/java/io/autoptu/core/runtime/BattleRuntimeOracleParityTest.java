package io.autoptu.core.runtime;

import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class BattleRuntimeOracleParityTest {
    @Test
    void runtimeShiftDispatchMatchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.shift.application.oracle", "");
        assumeTrue(!oraclePath.isBlank(), "shift-application oracle path not configured");

        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(oraclePath))) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            expected.put(parts[0], parts.length == 2 ? parts[1] : "");
        }
        assertEquals(expected, generatedFixtures());
    }

    private static Map<String, String> generatedFixtures() {
        Map<String, String> rows = new LinkedHashMap<>();

        BattleRuntimeState state = state(Set.of(), 3);
        BattleRuntime.applyAction(state, new ShiftChoice("actor", new GridCoord(2, 1)), ignored -> true);
        RuntimeCombatantState actor = state.requireCombatant("actor");
        rows.put("successful_shift", actor.position().x() + "," + actor.position().y() + "|used=true");
        rows.put("second_shift_rejected", rejected(() -> BattleRuntime.applyAction(
                state, new ShiftChoice("actor", new GridCoord(3, 1)), ignored -> true)));

        BattleRuntimeState blocked = state(Set.of(new GridCoord(2, 1)), 3);
        rows.put("blocked_rejected", rejected(() -> BattleRuntime.applyAction(
                blocked, new ShiftChoice("actor", new GridCoord(2, 1)), ignored -> true)));

        BattleRuntimeState tooFar = state(Set.of(), 3);
        rows.put("too_far_rejected", rejected(() -> BattleRuntime.applyAction(
                tooFar, new ShiftChoice("actor", new GridCoord(5, 5)), ignored -> true)));
        return rows;
    }

    private static BattleRuntimeState state(Set<GridCoord> blockers, int overland) {
        RuntimeCombatantState actor = new RuntimeCombatantState(
                "actor",
                MovementProfile.walking(new GridCoord(1, 1), overland),
                10,
                10,
                new ActionBudget()
        );
        return new BattleRuntimeState(new MovementGrid(6, 6, blockers, Map.of()), List.of(actor));
    }

    private static String rejected(Runnable action) {
        try {
            action.run();
            return "false";
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return "true";
        }
    }
}
