package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeldItemStartRuleProfileOracleParityTest {
    @Test
    void supportedStartProfileMatchesPinnedPythonParser() throws IOException {
        String fixturePath = System.getProperty("autoptu.held.item.start.profile.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());

        for (String line : Files.readAllLines(Path.of(fixturePath))) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            assertEquals(11, parts.length, "fixture column count for " + line);

            String scenario = parts[0];
            HeldItemStartRuleProfile actual = HeldItemStartRuleProfileParser.parse(parts[10]);

            assertEquals(parts[1], encodeAmounts(actual.baseStatChanges()), scenario + " base stat changes");
            assertEquals(parts[2], encodeScalars(actual.baseStatScalars()), scenario + " base stat scalars");
            assertEquals(parts[3], encode(actual.accuracyBonus()), scenario + " accuracy");
            assertEquals(parts[4], encode(actual.accuracyBonusVsLowerAv()), scenario + " lower AV accuracy");
            assertEquals(parts[5], encodeTyped(actual.typeAccuracyBonus()), scenario + " typed accuracy");
            assertEquals(parts[6], encode(actual.statusEvasionBonus()), scenario + " status evasion");
            assertEquals(parts[7], encode(actual.allEvasionBonus()), scenario + " all evasion");
            assertEquals(parts[8], encode(actual.initiativeBonus()), scenario + " initiative");
            assertEquals(parts[9], encode(actual.speedScalar()), scenario + " speed scalar");
        }
    }

    private static String encodeAmounts(List<HeldItemStartTemporaryEffectResolution.StatAmount> values) {
        if (values == null || values.isEmpty()) return "-";
        return values.stream().map(row -> row.stat() + ":" + row.amount()).reduce((a, b) -> a + ";" + b).orElse("-");
    }

    private static String encodeScalars(List<HeldItemStartTemporaryEffectResolution.StatScalar> values) {
        if (values == null || values.isEmpty()) return "-";
        return values.stream().map(row -> row.stat() + ":" + row.multiplier()).reduce((a, b) -> a + ";" + b).orElse("-");
    }

    private static String encodeTyped(HeldItemStartTemporaryEffectResolution.TypeAmount value) {
        return value == null ? "-" : value.type() + ":" + value.amount();
    }

    private static String encode(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
