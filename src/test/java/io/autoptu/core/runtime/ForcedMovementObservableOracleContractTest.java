package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freezes the language-neutral observable structure of Python apply_forced_movement. */
class ForcedMovementObservableOracleContractTest {
    @Test
    void freezesPinnedPythonObservableInventory() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_OBSERVABLE_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() > 1, "forced movement observable fixture is empty");
        assertEquals("path\tfunction\tline\tkind\tsymbol\tstatement", lines.getFirst());

        int previousLine = -1;
        List<String> kinds = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            assertEquals(6, fields.length, "forced movement observable row shape changed");
            assertEquals("auto_ptu/rules/battle_state.py", fields[0]);
            assertEquals("apply_forced_movement", fields[1]);
            int sourceLine = Integer.parseInt(fields[2]);
            assertTrue(sourceLine >= previousLine, "observable inventory must remain source ordered");
            previousLine = sourceLine;
            assertFalse(fields[3].isBlank(), "observable kind must be explicit");
            assertFalse(fields[5].isBlank(), "observable statement must be frozen");
            kinds.add(fields[3]);
        }

        assertTrue(kinds.contains("return"), "Python forced movement return contract disappeared");
        assertTrue(kinds.contains("call"), "Python forced movement call effects disappeared");
        assertTrue(
                kinds.contains("write") || kinds.contains("position_write"),
                "Python forced movement state writes disappeared"
        );
    }
}
