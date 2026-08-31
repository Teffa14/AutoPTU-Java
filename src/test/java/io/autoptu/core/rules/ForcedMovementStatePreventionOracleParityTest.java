package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcedMovementStatePreventionOracleParityTest {
    @Test
    void matchesPinnedPythonStatusAndTemporaryPreventionFamilies() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_STATE_PREVENTION_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() >= 3, "forced movement state-prevention fixture is incomplete");
        String all = String.join("\n", lines);
        assertTrue(all.contains("Ingrain"), "pinned oracle must expose Ingrain prevention");
        assertTrue(all.contains("push_immunity"), "pinned oracle must expose push_immunity prevention");

        ForcedMovementInstruction push = new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 1);
        ForcedMovementInstruction pull = new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PULL, 1);

        assertTrue(ForcedMovementPreventionResolution.preventedByState(
                push, Set.of("Ingrain"), List.of(), 4
        ));
        assertTrue(ForcedMovementPreventionResolution.preventedByState(
                pull, Set.of("ingrain"), List.of(), 4
        ));

        ForcedMovementPreventionResolution.TemporaryEffect active =
                new ForcedMovementPreventionResolution.TemporaryEffect("push_immunity", 4, "Anchor Rule");
        ForcedMovementPreventionResolution.TemporaryEffect expired =
                new ForcedMovementPreventionResolution.TemporaryEffect("push_immunity", 3, "Anchor Rule");

        assertTrue(ForcedMovementPreventionResolution.preventedByState(
                push, Set.of(), List.of(active), 4
        ));
        assertFalse(ForcedMovementPreventionResolution.preventedByState(
                pull, Set.of(), List.of(active), 4
        ));
        assertFalse(ForcedMovementPreventionResolution.preventedByState(
                push, Set.of(), List.of(expired), 4
        ));
    }
}
