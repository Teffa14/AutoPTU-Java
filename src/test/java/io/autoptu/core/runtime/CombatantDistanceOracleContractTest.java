package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freezes the Python combatant-to-coordinate geometry used by shadow_tag_anchor. */
class CombatantDistanceOracleContractTest {
    @Test
    void freezesPinnedCombatantDistanceHelper() throws IOException {
        String fixture = System.getenv("AUTOPTU_COMBATANT_DISTANCE_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertFalse(lines.isEmpty());
        assertEquals("path\tfunction\trole\tline\tcontract", lines.getFirst());
        assertTrue(lines.size() >= 4, "distance fixture must expose signature, implementation and return behavior");

        boolean sawSignature = false;
        boolean sawImplementation = false;
        boolean sawReturn = false;
        boolean sawFootprintCall = false;
        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            assertEquals(5, fields.length, "combatant distance fixture row shape changed");
            assertEquals("auto_ptu/rules/battle_state.py", fields[0]);
            assertEquals("_combatant_distance_to_coord", fields[1]);
            assertFalse(fields[4].isBlank());
            switch (fields[2]) {
                case "signature" -> {
                    sawSignature = true;
                    assertTrue(fields[4].contains("position"), "candidate-position projection must remain explicit");
                }
                case "implementation" -> {
                    sawImplementation = true;
                    assertTrue(fields[4].contains("position if position is not None else actor.position"));
                    assertTrue(fields[4].contains("targeting.footprint_distance"));
                    assertTrue(fields[4].contains("getattr(actor.spec, 'size', '')"));
                    assertTrue(fields[4].contains("coord, 'Medium', self.grid"));
                }
                case "return" -> sawReturn = true;
                case "call:footprint_distance" -> {
                    sawFootprintCall = true;
                    assertTrue(fields[4].contains("coord, 'Medium', self.grid"));
                }
                default -> { }
            }
        }
        assertTrue(sawSignature);
        assertTrue(sawImplementation);
        assertTrue(sawReturn);
        assertTrue(sawFootprintCall);
    }
}
