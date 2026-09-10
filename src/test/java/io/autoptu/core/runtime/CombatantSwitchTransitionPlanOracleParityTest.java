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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CombatantSwitchTransitionPlanOracleParityTest {
    @Test
    void planMatchesPinnedPythonApplySwitchPresenceAndDestination() throws IOException {
        Path fixture = Path.of("build/oracle/impostor-switch-entry.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        RuntimeCombatantState outgoing = combatant("a-1", new GridCoord(2, 2));
        // Java movement profiles are currently non-null even while inactive. The transition plan
        // freezes Python's off-field contract without leaking a placeholder into rule decisions.
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

        CombatantSwitchTransitionPlan plan = CombatantSwitchTransitionPlan.resolve(state, "a-1", "a-2");
        assertEquals(expected.get("OUTGOING_ACTIVE"), plan.outgoingActiveAfter() ? "1" : "0");
        assertEquals(expected.get("OUTGOING_POSITION_IS_NONE"), plan.outgoingOffFieldAfter() ? "1" : "0");
        assertEquals(expected.get("REPLACEMENT_ACTIVE"), plan.replacementActiveAfter() ? "1" : "0");
        assertEquals(expected.get("POSITION"), plan.replacementDestination().x() + "," + plan.replacementDestination().y());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 4),
                20,
                20,
                new ActionBudget()
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            int separator = line.indexOf('\t');
            if (separator < 0) throw new IllegalArgumentException("invalid fixture row: " + line);
            values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return Map.copyOf(values);
    }
}
