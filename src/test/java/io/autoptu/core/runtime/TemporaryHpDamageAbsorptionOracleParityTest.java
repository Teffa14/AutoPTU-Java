package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TemporaryHpDamageAbsorptionOracleParityTest {
    @Test
    void matchesPinnedPythonOrdinaryDamageTemporaryHpAbsorption() throws IOException {
        String oracle = System.getProperty("autoptu.temporary.hp.damage.absorption.oracle");
        assumeTrue(oracle != null && !oracle.isBlank(), "Temporary HP absorption fixture not configured");

        List<String> lines = Files.readAllLines(Path.of(oracle));
        assertEquals(
                "case\ttemporary_hp\tincoming_damage\tpending_damage\tabsorbed_damage\tremaining_damage\tremaining_temporary_hp",
                lines.getFirst()
        );
        assertEquals(5, lines.size(), "Expected header plus four frozen oracle cases");

        for (String line : lines.subList(1, lines.size())) {
            String[] fields = line.split("\\t", -1);
            String caseName = fields[0];
            int temporaryHp = Integer.parseInt(fields[1]);
            int incomingDamage = Integer.parseInt(fields[2]);

            TemporaryHpDamageAbsorption.Result actual =
                    TemporaryHpDamageAbsorption.resolve(temporaryHp, incomingDamage);

            assertEquals(Integer.parseInt(fields[3]), actual.pendingDamage(), caseName + " pending damage");
            assertEquals(Integer.parseInt(fields[4]), actual.absorbedDamage(), caseName + " absorbed damage");
            assertEquals(Integer.parseInt(fields[5]), actual.remainingDamage(), caseName + " remaining damage");
            assertEquals(
                    Integer.parseInt(fields[6]),
                    actual.remainingTemporaryHp(),
                    caseName + " remaining temporary HP"
            );
        }
    }
}
