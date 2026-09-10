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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatantFieldPresenceStoreOracleParityTest {
    @Test
    void materializedSwitchPresenceMatchesPinnedPythonApplySwitch() throws IOException {
        Path fixture = Path.of("build/oracle/switch-entry-state.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

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

        CombatantSwitchTransitionPlan plan = CombatantSwitchTransitionPlan.resolve(state, "a-1", "a-2");
        CombatantFieldPresenceStore presence = new CombatantFieldPresenceStore(Map.of("a-1", outgoing.position()));
        presence.applySwitchTransitionFromRuntime(plan);

        assertEquals(expected.get("OUTGOING_POSITION_IS_NONE"), presence.isOnField("a-1") ? "0" : "1");
        assertEquals(expected.get("REPLACEMENT_ACTIVE"), presence.isOnField("a-2") ? "1" : "0");
        assertFalse(presence.position("a-1").isPresent());
        assertTrue(presence.position("a-2").isPresent());
        GridCoord replacementPosition = presence.position("a-2").orElseThrow();
        assertEquals(expected.get("POSITION"), replacementPosition.x() + "," + replacementPosition.y());
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(id, MovementProfile.walking(position, 4), 20, 20, new ActionBudget());
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
