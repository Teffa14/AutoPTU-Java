package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaTrapTargetingContractOracleParityTest {
    @Test
    void regularArenaTrapTargetEligibilityMatchesPinnedPython() throws IOException {
        Path fixture = Path.of("build/oracle/arena-trap-targeting.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));

        List<ArenaTrapTargetingContract.Candidate> candidates = List.of(
                candidate("normal", "foes", true, false, 1, List.of("Normal"), List.of(), List.of(), 0, 0),
                candidate("far", "foes", true, false, 7, List.of("Normal"), List.of(), List.of(), 0, 0),
                candidate("flying", "foes", true, false, 2, List.of("Flying"), List.of(), List.of(), 0, 0),
                candidate("levitate", "foes", true, false, 3, List.of("Normal"), List.of("Levitate"), List.of(), 0, 0),
                candidate("sky4", "foes", true, false, 1, List.of("Normal"), List.of(), List.of(), 4, 0),
                candidate("burrow4", "foes", true, false, 1, List.of("Normal"), List.of(), List.of(), 0, 4),
                candidate("inactive", "foes", false, false, 1, List.of("Normal"), List.of(), List.of(), 0, 0),
                candidate("ally", "players", true, false, 1, List.of("Normal"), List.of(), List.of(), 0, 0)
        );

        assertEquals(
                expected.get("SLOWED"),
                String.join(",", ArenaTrapTargetingContract.regularTargets("players", candidates))
        );
    }

    @Test
    void errataRangeAndActivationAreExplicitContractInputs() {
        List<ArenaTrapTargetingContract.Candidate> candidates = List.of(
                candidate("near", "foes", true, false, 5, List.of("Normal"), List.of(), List.of(), 0, 0),
                candidate("far", "foes", true, false, 6, List.of("Normal"), List.of(), List.of(), 0, 0)
        );
        assertEquals(List.of(), ArenaTrapTargetingContract.errataTargets("players", false, candidates));
        assertEquals(List.of("near"), ArenaTrapTargetingContract.errataTargets("players", true, candidates));
    }

    private static ArenaTrapTargetingContract.Candidate candidate(
            String id,
            String team,
            boolean active,
            boolean fainted,
            int distance,
            List<String> types,
            List<String> abilities,
            List<String> capabilities,
            int sky,
            int burrow
    ) {
        return new ArenaTrapTargetingContract.Candidate(
                id, team, active, fainted, distance, types, abilities, capabilities, sky, burrow
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return Map.copyOf(result);
    }
}
