package io.autoptu.core.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ForcedMovementInstructionOracleParityTest {
    @Test
    void deterministicContractCoversPriorityDistanceAndRawKeywordIdentity() {
        assertEquals(
                Optional.of(new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 3)),
                ForcedMovementInstructionResolution.resolve(List.of(), "Push the target 3 meters.")
        );
        assertEquals(
                Optional.of(new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 1)),
                ForcedMovementInstructionResolution.resolve(List.of("pull", "push"), "")
        );
        assertTrue(ForcedMovementInstructionResolution.resolve(List.of(" Push "), "").isEmpty());
    }

    @Test
    void matchesPinnedPythonOracleWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getProperty("autoptu.forced.movement.instruction.oracle");
        if (fixturePath == null || fixturePath.isBlank()) {
            fixturePath = System.getenv("AUTOPTU_FORCED_MOVEMENT_INSTRUCTION_ORACLE");
        }
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertTrue(lines.size() > 1, "forced movement oracle fixture must contain cases");
        assertEquals("case\tkeywords\teffects_text\tkind\tdistance", lines.getFirst());

        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(5, parts.length, "malformed fixture row: " + line);
            List<String> keywords = parts[1].isEmpty()
                    ? List.of()
                    : Arrays.asList(parts[1].split("\\|", -1));
            Optional<ForcedMovementInstruction> actual =
                    ForcedMovementInstructionResolution.resolve(keywords, parts[2]);

            if (parts[3].isEmpty()) {
                assertTrue(actual.isEmpty(), parts[0]);
                continue;
            }
            assertTrue(actual.isPresent(), parts[0]);
            ForcedMovementInstruction.Kind expectedKind =
                    ForcedMovementInstruction.Kind.valueOf(parts[3].toUpperCase());
            assertEquals(expectedKind, actual.orElseThrow().kind(), parts[0]);
            assertEquals(Integer.parseInt(parts[4]), actual.orElseThrow().distance(), parts[0]);
        }
    }
}
