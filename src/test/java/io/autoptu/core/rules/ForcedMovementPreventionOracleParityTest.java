package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcedMovementPreventionOracleParityTest {
    @Test
    void matchesPinnedPythonDefenderPreventionFamily() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_PREVENTION_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() >= 4, "forced movement prevention fixture is incomplete");
        String all = String.join("\n", lines);
        assertTrue(all.contains("Suction Cups"), "pinned oracle must expose Suction Cups prevention");
        assertTrue(all.contains("Sumo Stance"), "pinned oracle must expose Sumo Stance prevention");
        assertTrue(all.contains("Insectoid Utility"), "pinned oracle must expose Insectoid Utility prevention");
        assertTrue(all.contains("Wallclimber"), "pinned oracle must expose Wallclimber capability guard");
        assertTrue(all.contains("has_trainer_feature"), "composite guard must consult Trainer Feature state");
        assertTrue(all.contains("has_capability"), "composite guard must consult capability state");

        ForcedMovementInstruction push = new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 1);
        ForcedMovementInstruction pull = new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PULL, 1);

        assertTrue(ForcedMovementPreventionResolution.prevented(push, List.of("Suction Cups"), false));
        assertTrue(ForcedMovementPreventionResolution.prevented(push, List.of("Suction Cups [Errata]"), false));
        assertTrue(ForcedMovementPreventionResolution.prevented(push, List.of("Sumo Stance"), false));
        assertFalse(ForcedMovementPreventionResolution.prevented(push, List.of("Suction Cups"), true));
        assertFalse(ForcedMovementPreventionResolution.prevented(pull, List.of("Suction Cups"), false));
    }
}
