package io.autoptu.core.oracle;

import io.autoptu.core.event.BattleEventFactory;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.DamageDice;
import io.autoptu.core.model.DamageResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveEventOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonMoveEventFields() throws IOException {
        String fixturePath = System.getProperty("autoptu.move.events.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python move-event oracle fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java move-event scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Move-event parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("miss", BattleEventFactory.moveResolved(
                "Player", "pikachu", "bulbasaur", "thunder-shock",
                new AccuracyResult(false, false, 12, 6), null, 35
        ).stableKey());
        out.put("hit", BattleEventFactory.moveResolved(
                "Player", "pikachu", "bulbasaur", "thunder-shock",
                new AccuracyResult(true, false, 8, 6), damage(12), 23
        ).stableKey());
        out.put("critical", BattleEventFactory.moveResolved(
                "Foe", "charizard", "venusaur", "flamethrower",
                new AccuracyResult(true, true, 20, 6), damage(20), 15
        ).stableKey());
        return out;
    }

    private static DamageResult damage(int value) {
        return new DamageResult(new DamageDice(1, 6, 0), value, 0, value, value, value, value);
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid move-event fixture: " + line);
            out.put(parts[0], parts[1]);
        }
        return out;
    }
}
