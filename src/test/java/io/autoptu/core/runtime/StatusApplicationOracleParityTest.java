package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StatusApplicationOracleParityTest {
    @Test
    void innerFocusFlinchPreventionMatchesPinnedPythonContract() throws IOException {
        Map<String, Integer> oracle = readOracle();
        assertEquals(1, oracle.get("inner_focus_checks_flinch_alias_set"));
        assertEquals(1, oracle.get("inner_focus_emits_status_block"));
        assertEquals(1, oracle.get("inner_focus_returns_before_status_write"));
        assertEquals(1, oracle.get("flinch_application_records_applied_round"));

        BattleRuntimeState state = state();
        StatusEntry flinch = new StatusEntry("Flinch", Map.of("applied_round", 7));
        StatusApplicationResult result = StatusApplicationResolution.apply(
                state,
                BuiltinStatusApplicationHooks.registry(),
                "source",
                "target",
                flinch,
                "move",
                "Fake Out",
                "fake-out"
        );

        assertFalse(result.applied());
        assertFalse(state.hasStatus("target", "flinch"));
        assertEquals("rule_effect|ability|inner focus|target|target|fake-out|status_block|0.0|20",
                result.events().getFirst().stableKey());
    }

    private static Map<String, Integer> readOracle() throws IOException {
        String property = System.getProperty("autoptu.status.application.oracle");
        if (property == null || property.isBlank()) {
            throw new IllegalStateException("autoptu.status.application.oracle is required");
        }
        Map<String, Integer> result = new HashMap<>();
        List<String> lines = Files.readAllLines(Path.of(property));
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] parts = lines.get(i).split("\\t");
            result.put(parts[0], Integer.parseInt(parts[1]));
        }
        return result;
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState source = combatant("source", List.of());
        RuntimeCombatantState target = combatant("target", List.of("Inner Focus"));
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(source, target)
        );
    }

    private static RuntimeCombatantState combatant(String id, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 3),
                20,
                20,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of("Normal"),
                List.of(),
                abilities
        );
    }
}
