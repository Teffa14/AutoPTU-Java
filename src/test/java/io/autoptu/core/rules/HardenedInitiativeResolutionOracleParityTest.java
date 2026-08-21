package io.autoptu.core.rules;

import io.autoptu.core.runtime.TemporaryEffectEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HardenedInitiativeResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.hardened.initiative.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\tcurrent_round\tinjuries\thardened_expiry\tpress_on_feature\tpress_on_active\tintimidate_rank\texpected_bonus",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            int currentRound = Integer.parseInt(parts[1]);
            int injuries = Integer.parseInt(parts[2]);
            String expiry = parts[3];
            boolean pressOnFeature = "1".equals(parts[4]);
            boolean pressOnActive = "1".equals(parts[5]);
            int intimidateRank = Integer.parseInt(parts[6]);
            int expected = Integer.parseInt(parts[7]);

            ArrayList<TemporaryEffectEntry> effects = new ArrayList<>();
            effects.add(expiry.isBlank()
                    ? new TemporaryEffectEntry("hardened")
                    : new TemporaryEffectEntry("hardened", Map.of("expires_round", scalarExpiry(expiry))));
            if (pressOnActive) effects.add(new TemporaryEffectEntry("press_on_active"));

            int actual = HardenedInitiativeResolution.resolve(
                    currentRound,
                    injuries,
                    effects,
                    pressOnFeature,
                    intimidateRank
            );
            assertEquals(expected, actual, name);
        }
    }

    private static Object scalarExpiry(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return text;
        }
    }
}
