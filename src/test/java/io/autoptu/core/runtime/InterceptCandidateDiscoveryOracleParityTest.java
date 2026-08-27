package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterceptCandidateDiscoveryOracleParityTest {
    @Test
    void candidateDiscoveryContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.candidate.discovery.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        for (String key : new String[]{
                "kind_melee_else_ranged",
                "attacker_no_intercept_precedes_candidates",
                "no_intercept_expires_strictly_after_round",
                "no_intercept_removes_expired",
                "candidate_skips_target",
                "candidate_requires_positive_hp",
                "candidate_requires_same_team",
                "weaponize_requires_ability",
                "weaponize_requires_living_weapon",
                "weaponize_controller_is_target",
                "weaponize_continues_after_append",
                "ready_matches_ally",
                "ready_matches_kind",
                "sentinel_expires_strictly_after_round",
                "sentinel_removes_expired",
                "sentinel_requires_base_or_extra_shift",
                "sentinel_marks_uses_shift",
                "sources_require_can_intercept",
                "sources_require_loyalty"
        }) {
            assertEquals(1, fixture.get(key), key);
        }
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.equals("key\tvalue")) continue;
            String[] parts = line.split("\\t");
            result.put(parts[0], Integer.parseInt(parts[1]));
        }
        return result;
    }
}
