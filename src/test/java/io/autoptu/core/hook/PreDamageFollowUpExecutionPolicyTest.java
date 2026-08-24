package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PreDamageFollowUpExecutionPolicyTest {
    @Test
    void pythonParityPolicyPreservesNestedResolutionWithoutSecondResourceSpend() {
        PreDamageFollowUpExecutionPolicy policy = PreDamageFollowUpExecutionPolicy.pythonParity();

        assertTrue(policy.synchronous());
        assertTrue(policy.reuseOriginalMove());
        assertTrue(policy.runPreDamageReactions());
        assertFalse(policy.spendAction());
        assertFalse(policy.spendMoveFrequency());
    }

    @Test
    void matchesPinnedPythonContractWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_PRE_DAMAGE_FOLLOW_UP_ORACLE");
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
                "sway_reenters_resolve_move_targets",
                "magic_coat_reenters_resolve_move_targets",
                "sway_reuses_original_move",
                "magic_coat_reuses_original_move",
                "sway_redirects_attacker_into_self",
                "magic_coat_swaps_defender_into_attacker",
                "sway_follow_up_is_synchronous",
                "magic_coat_follow_up_is_synchronous",
                "sway_swallows_follow_up_errors",
                "magic_coat_swallows_follow_up_errors",
                "resolve_targets_does_not_mark_actions",
                "resolve_targets_does_not_record_move_frequency",
                "resolve_targets_runs_pre_damage_interrupts"
        )) {
            assertEquals(1, values.get(property), property);
        }
    }
}
