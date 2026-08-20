package io.autoptu.core.rules;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InitiativeSpeedAbilityResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonInitiativeSpeedAbilityFixtures() throws IOException {
        String oraclePath = System.getProperty("autoptu.initiative.speed.ability.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\tbase_speed\thp\tmax_hp\tweather\tterrain\tgrounded\tabilities\texpected_speed",
                lines.getFirst()
        );
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            Integer hp = parts[2].isBlank() ? null : Integer.valueOf(parts[2]);
            List<String> abilities = parts[7].isBlank() ? List.of() : List.of(parts[7].split(","));

            int actual = InitiativeSpeedAbilityResolution.resolve(
                    Integer.parseInt(parts[1]),
                    hp,
                    Integer.parseInt(parts[3]),
                    parts[4],
                    parts[5],
                    "1".equals(parts[6]),
                    abilities
            );
            assertEquals(Integer.parseInt(parts[8]), actual, name);
        }
    }

    @Test
    void chlorophyllRequiresTheExactErrataVariant() {
        assertEquals(12, InitiativeSpeedAbilityResolution.resolve(
                12, 100, 100, "Sunny", "", true, List.of("Chlorophyll")
        ));
        assertEquals(24, InitiativeSpeedAbilityResolution.resolve(
                12, 100, 100, "Sunny", "", true, List.of("Chlorophyll [Errata]")
        ));
    }
}
