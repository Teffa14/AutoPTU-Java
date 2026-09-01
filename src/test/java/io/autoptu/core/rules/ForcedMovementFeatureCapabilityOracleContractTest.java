package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcedMovementFeatureCapabilityOracleContractTest {
    @Test
    void freezesPinnedPythonCompositeFeatureCapabilityGuard() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_FEATURE_CAPABILITY_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertTrue(lines.size() >= 2, "forced movement feature/capability fixture is incomplete");
        String all = String.join("\n", lines);
        assertTrue(all.contains("Insectoid Utility"), "pinned oracle must expose Insectoid Utility");
        assertTrue(all.contains("Wallclimber"), "pinned oracle must expose Wallclimber");
        assertTrue(all.contains("has_trainer_feature"), "guard must consult canonical Trainer Feature state");
        assertTrue(all.contains("has_capability"), "guard must consult canonical capability state");
    }
}
