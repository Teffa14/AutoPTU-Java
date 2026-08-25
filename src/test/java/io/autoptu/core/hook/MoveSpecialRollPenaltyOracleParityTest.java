package io.autoptu.core.hook;

import io.autoptu.core.runtime.TemporaryEffectStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveSpecialRollPenaltyOracleParityTest {
    @Test
    void rollPenaltyAndExpiryMatchPinnedPython() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.move.special.roll.penalty.oracle",
                "build/oracle/move-special-roll-penalty.tsv"));
        Assumptions.assumeTrue(Files.exists(fixture));

        Map<String, Expected> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            expected.put(parts[0], new Expected(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
        }

        assertCase(expected, "baseline", 5, new TemporaryEffectStore());

        TemporaryEffectStore stacked = new TemporaryEffectStore();
        stacked.add("all_roll_penalty", Map.of("amount", 2));
        stacked.add("all_roll_penalty", Map.of("amount", 3));
        assertCase(expected, "stacked", 5, stacked);

        TemporaryEffectStore numericString = new TemporaryEffectStore();
        numericString.add("all_roll_penalty", Map.of("amount", "4"));
        assertCase(expected, "numeric_string", 5, numericString);

        TemporaryEffectStore invalidAmount = new TemporaryEffectStore();
        invalidAmount.add("all_roll_penalty", Map.of("amount", "bad"));
        invalidAmount.add("all_roll_penalty", Map.of("amount", 2));
        assertCase(expected, "invalid_amount", 5, invalidAmount);

        TemporaryEffectStore negativeClamp = new TemporaryEffectStore();
        negativeClamp.add("all_roll_penalty", Map.of("amount", -5));
        negativeClamp.add("all_roll_penalty", Map.of("amount", 2));
        assertCase(expected, "negative_clamp", 5, negativeClamp);

        TemporaryEffectStore sameRound = new TemporaryEffectStore();
        sameRound.add("all_roll_penalty", Map.of("amount", 4, "expires_round", 5));
        assertCase(expected, "same_round_kept", 5, sameRound);

        TemporaryEffectStore expired = new TemporaryEffectStore();
        expired.add("all_roll_penalty", Map.of("amount", 7, "expires_round", 4));
        expired.add("all_roll_penalty", Map.of("amount", 2));
        assertCase(expected, "expired_removed", 5, expired);
    }

    private static void assertCase(
            Map<String, Expected> expected,
            String caseId,
            int currentRound,
            TemporaryEffectStore effects
    ) {
        Expected row = expected.get(caseId);
        assertEquals(row.penalty(), MoveSpecialRollPenaltyResolution.resolve(effects, currentRound), caseId + " penalty");
        assertEquals(row.remainingEntries(), effects.entriesInInsertionOrder().size(), caseId + " remaining entries");
    }

    private record Expected(int penalty, int remainingEntries) {}
}
