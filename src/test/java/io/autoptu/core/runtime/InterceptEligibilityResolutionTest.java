package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterceptEligibilityResolutionTest {
    @Test
    void oracleContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.eligibility.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        for (String key : new String[]{
                "can_blocks_fainted",
                "can_blocks_paralyzed",
                "can_blocks_stuck",
                "can_blocks_tripped",
                "can_blocks_sleep_family",
                "can_blocks_flinch_family",
                "can_blocks_trapped_family",
                "loyalty_reads_coaching_intercept",
                "loyalty_missing_allows",
                "loyalty_requires_three",
                "other_controller_requires_six"
        }) {
            assertEquals(1, fixture.get(key), key);
        }
    }

    @Test
    void loyaltyThresholdsMatchPython() {
        assertFalse(resolve(2, true, false).allowed());
        assertTrue(resolve(3, true, false).allowed());
        assertFalse(resolve(5, false, false).allowed());
        assertTrue(resolve(6, false, false).allowed());
        assertTrue(resolve(null, false, false).allowed());
        assertTrue(resolve(1, false, true).allowed());
    }

    @Test
    void statusAndMobilityGuardsPrecedeLoyalty() {
        InterceptEligibilityResolution.Result fainted = InterceptEligibilityResolution.resolve(
                input(6, true, true, true, false, false, false, false, false, false)
        );
        assertFalse(fainted.allowed());
        assertEquals(InterceptEligibilityResolution.BlockReason.FAINTED, fainted.blockReason());

        for (InterceptEligibilityResolution.Input blocked : new InterceptEligibilityResolution.Input[]{
                input(6, true, false, false, true, false, false, false, false, false),
                input(6, true, false, false, false, true, false, false, false, false),
                input(6, true, false, false, false, false, true, false, false, false),
                input(6, true, false, false, false, false, false, true, false, false),
                input(6, true, false, false, false, false, false, false, true, false)
        }) {
            InterceptEligibilityResolution.Result result = InterceptEligibilityResolution.resolve(blocked);
            assertFalse(result.allowed());
            assertEquals(InterceptEligibilityResolution.BlockReason.INCAPACITATED, result.blockReason());
        }

        InterceptEligibilityResolution.Result trapped = InterceptEligibilityResolution.resolve(
                input(6, true, false, false, false, false, false, false, false, true)
        );
        assertFalse(trapped.allowed());
        assertEquals(InterceptEligibilityResolution.BlockReason.IMMOBILIZED, trapped.blockReason());
    }

    private static InterceptEligibilityResolution.Result resolve(Integer loyalty, boolean sameController, boolean coached) {
        return InterceptEligibilityResolution.resolve(
                input(loyalty, sameController, coached, false, false, false, false, false, false, false)
        );
    }

    private static InterceptEligibilityResolution.Input input(
            Integer loyalty,
            boolean sameController,
            boolean coached,
            boolean fainted,
            boolean paralyzed,
            boolean stuck,
            boolean tripped,
            boolean sleeping,
            boolean flinched,
            boolean trapped
    ) {
        return new InterceptEligibilityResolution.Input(
                loyalty,
                sameController,
                coached,
                fainted,
                paralyzed,
                stuck,
                tripped,
                sleeping,
                flinched,
                trapped
        );
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
