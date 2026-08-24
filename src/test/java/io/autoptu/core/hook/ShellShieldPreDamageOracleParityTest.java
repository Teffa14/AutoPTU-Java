package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShellShieldPreDamageOracleParityTest {
    @Test
    void matchesPinnedPythonContractWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_SHELL_SHIELD_PRE_DAMAGE_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertEquals("property\tvalue", lines.getFirst());
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(2, parts.length, "malformed row: " + line);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }

        for (String property : List.of(
                "requires_shell_shield_ready",
                "decision_precedes_ready_consumption",
                "declined_decision_preserves_ready",
                "ready_payload_controls_event_ability_name",
                "adds_withdrawn_only_when_missing",
                "raises_defense_on_self_by_one",
                "combat_stage_mutation_precedes_ability_event",
                "emits_withdraw_ability_event",
                "does_not_cancel_hit",
                "does_not_zero_damage",
                "does_not_zero_type_multiplier"
        )) {
            assertEquals(1, values.get(property), property);
        }
    }
}
