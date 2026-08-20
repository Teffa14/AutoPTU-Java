package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrainerInitiativeSpeedOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.trainer.initiative.speed.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals("name\ttrainer_speed\tactive_speeds\troster_speeds\texpected", lines.getFirst());

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            Integer trainerSpeed = parts[1].isBlank() ? null : Integer.valueOf(parts[1]);
            List<Integer> activeSpeeds = parseSpeeds(parts[2]);
            List<Integer> rosterSpeeds = parseSpeeds(parts[3]);
            int expected = Integer.parseInt(parts[4]);

            assertEquals(
                    expected,
                    TrainerInitiativeSpeedResolution.resolve(trainerSpeed, activeSpeeds, rosterSpeeds),
                    name
            );
        }
    }

    private static List<Integer> parseSpeeds(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        return Arrays.stream(encoded.split(","))
                .map(String::strip)
                .map(Integer::valueOf)
                .toList();
    }
}
