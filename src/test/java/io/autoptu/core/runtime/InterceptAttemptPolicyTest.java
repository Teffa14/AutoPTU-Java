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

class InterceptAttemptPolicyTest {
    @Test
    void attemptContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.attempt.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));

        assertEquals(1, fixture.get("blocks_cannot_miss"));
        assertEquals(1, fixture.get("blocks_area_attacks"));
        assertEquals(1, fixture.get("distinguishes_melee_and_ranged"));
        assertEquals(1, fixture.get("priority_interrupt_has_speed_gate"));
        assertEquals(1, fixture.get("priority_speed_gate_is_strictly_faster"));
    }

    @Test
    void blocksCannotMissBeforeGeometry() {
        InterceptAttemptPolicy.Result result = InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(true, false, "ranged", false, 10, 20));
        assertFalse(result.allowed());
        assertEquals(InterceptAttemptPolicy.BlockReason.CANNOT_MISS, result.blockReason());
    }

    @Test
    void blocksAreaAttackBeforeGeometry() {
        InterceptAttemptPolicy.Result result = InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, true, "ranged", false, 10, 20));
        assertFalse(result.allowed());
        assertEquals(InterceptAttemptPolicy.BlockReason.AREA_ATTACK, result.blockReason());
    }

    @Test
    void acceptsOrdinaryMeleeAndRangedAttacks() {
        assertTrue(InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, false, "melee", false, 1, 99)).allowed());
        assertTrue(InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, false, "Ranged", false, 1, 99)).allowed());
    }

    @Test
    void rejectsOtherTargetKinds() {
        InterceptAttemptPolicy.Result result = InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, false, "self", false, 20, 10));
        assertFalse(result.allowed());
        assertEquals(InterceptAttemptPolicy.BlockReason.UNSUPPORTED_TARGET_KIND, result.blockReason());
    }

    @Test
    void priorityOrInterruptRequiresStrictlyGreaterSpeed() {
        InterceptAttemptPolicy.Result slower = InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, false, "melee", true, 9, 10));
        InterceptAttemptPolicy.Result tied = InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, false, "melee", true, 10, 10));
        InterceptAttemptPolicy.Result faster = InterceptAttemptPolicy.resolve(
                new InterceptAttemptPolicy.Input(false, false, "melee", true, 11, 10));

        assertFalse(slower.allowed());
        assertFalse(tied.allowed());
        assertEquals(InterceptAttemptPolicy.BlockReason.PRIORITY_SPEED, tied.blockReason());
        assertTrue(faster.allowed());
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        Map<String, Integer> fixture = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("key\t")) continue;
            String[] parts = line.split("\\t", 2);
            fixture.put(parts[0], Integer.parseInt(parts[1]));
        }
        return fixture;
    }
}
