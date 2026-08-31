package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freezes shadow_tag_anchor setup and per-step geometry from the pinned Python oracle. */
class ForcedMovementShadowAnchorOracleContractTest {
    @Test
    void freezesPinnedShadowAnchorGeometry() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_SHADOW_ANCHOR_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertEquals("path\tfunction\trole\tline\tcondition\tstatement", lines.getFirst());
        assertEquals(3, lines.size(), "shadow anchor fixture must expose setup and candidate guard");

        Map<String, String[]> byRole = new HashMap<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            assertEquals(6, fields.length, "shadow anchor row shape changed");
            assertEquals("auto_ptu/rules/battle_state.py", fields[0]);
            assertEquals("apply_forced_movement", fields[1]);
            assertFalse(fields[5].isBlank(), "shadow anchor statement must be frozen");
            byRole.put(fields[2], fields);
        }

        assertTrue(byRole.containsKey("anchor_setup"));
        assertTrue(byRole.get("anchor_setup")[5].contains("shadow_tag_anchor"));

        assertTrue(byRole.containsKey("candidate_guard"));
        String guardCondition = byRole.get("candidate_guard")[4];
        assertFalse(guardCondition.isBlank(), "candidate guard condition must be explicit");
        assertTrue(guardCondition.contains("anchor_pos"), "anchor guard must depend on anchor_pos");
        assertTrue(guardCondition.contains("_combatant_distance_to_coord"),
                "anchor guard must freeze the Python combatant distance projection");
        assertTrue(byRole.get("candidate_guard")[5].contains("candidate"),
                "anchor restriction must be evaluated against each candidate step");
    }
}
