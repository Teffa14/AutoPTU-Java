package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ImpostorRandomChoiceOracleParityTest {
    @Test
    void matchesPinnedPythonChoiceAndNextBattleRandomValue() throws IOException {
        Path fixture = Path.of("build/oracle/transformation-state.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        long seed = Long.parseLong(expected.get("RNG_SEED"));
        List<String> abilities = Arrays.asList(expected.get("RNG_TARGET_ABILITIES").split(","));
        BattleRandomState randomState = new BattleRandomState(seed);

        String assigned = abilities.get(randomState.random().choiceIndex(abilities.size()));

        assertEquals(expected.get("RNG_ABILITY_ASSIGNED"), assigned);
        assertEquals(
                Double.parseDouble(expected.get("RNG_NEXT_RANDOM")),
                randomState.random().random()
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            int separator = line.indexOf('\t');
            if (separator < 0) throw new IllegalArgumentException("invalid fixture row: " + line);
            values.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return Map.copyOf(values);
    }
}
