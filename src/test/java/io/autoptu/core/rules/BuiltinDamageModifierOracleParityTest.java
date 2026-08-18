package io.autoptu.core.rules;

import io.autoptu.core.model.AttackModifier;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BuiltinDamageModifierOracleParityTest {
    @Test
    void burnDamageMatchesPinnedPythonOracle() throws IOException {
        String oraclePath = System.getProperty("autoptu.burn.damage.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertFalse(lines.isEmpty());
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            String name = columns[0];
            int baseDamage = Integer.parseInt(columns[1]);
            String category = columns[2];
            boolean burned = Boolean.parseBoolean(columns[3]);
            int expectedDamage = Integer.parseInt(columns[4]);

            List<AttackModifier> modifiers = BuiltinDamageModifierResolution.resolve(
                    category,
                    burned ? Set.of("Burned") : Set.of()
            );
            int actualDamage = Calculations.applyContextDamageModifiers(baseDamage, modifiers);
            assertEquals(expectedDamage, actualDamage, name);
        }
    }
}
