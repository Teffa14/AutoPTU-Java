package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForcedMovementAbilityModifierOracleParityTest {
    @Test
    void matchesPinnedPythonThrustBranch() throws IOException {
        String fixture = System.getenv("AUTOPTU_FORCED_MOVEMENT_ABILITY_MODIFIER_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            String[] fields = line.split("\\t", -1);
            String caseName = fields[0];
            boolean hasThrust = fields[1].equals("1");
            String category = fields[2];
            Optional<ForcedMovementInstruction> base = fields[3].isBlank()
                    ? Optional.empty()
                    : Optional.of(new ForcedMovementInstruction(
                            ForcedMovementInstruction.Kind.valueOf(fields[3].toUpperCase()),
                            Integer.parseInt(fields[4])
                    ));

            Optional<ForcedMovementInstruction> actual = ForcedMovementAbilityModifierResolution.resolve(
                    base,
                    category,
                    hasThrust ? List.of("Thrust") : List.of(),
                    false
            );

            if (fields[5].isBlank()) {
                assertEquals(Optional.empty(), actual, caseName);
            } else {
                ForcedMovementInstruction expected = new ForcedMovementInstruction(
                        ForcedMovementInstruction.Kind.valueOf(fields[5].toUpperCase()),
                        Integer.parseInt(fields[6])
                );
                assertEquals(Optional.of(expected), actual, caseName);
            }
        }
    }
}
