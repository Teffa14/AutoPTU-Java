package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InitiativeAdditionalBonusOracleParityTest {
    @Test
    void matchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.initiative.additional.bonus.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals("name\tspeed\tabilities\tagility\trider_doubled\thardened_bonus\texpected_bonus", lines.getFirst());

        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            int speed = Integer.parseInt(parts[1]);
            List<String> abilities = parts[2].isBlank()
                    ? List.of()
                    : Arrays.stream(parts[2].split(",")).map(String::strip).toList();
            boolean agility = "1".equals(parts[3]);
            boolean riderDoubled = "1".equals(parts[4]);
            int hardenedBonus = Integer.parseInt(parts[5]);
            int expectedBonus = Integer.parseInt(parts[6]);

            int actual = InitiativeAdditionalBonusResolution.resolve(
                    speed,
                    abilities,
                    agility,
                    riderDoubled,
                    hardenedBonus
            );
            assertEquals(expectedBonus, actual, name);
        }
    }
}
