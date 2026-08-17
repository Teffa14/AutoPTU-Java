package io.autoptu.core.oracle;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.JumpProfile;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.rules.Movement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JumpOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonJumpOracle() throws IOException {
        String fixturePath = System.getProperty("autoptu.jump.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python jump oracle fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java jump scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Jump parity failed for scenario: " + name);
        }
    }

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();

        result.put("long_basic_2", coords(Movement.legalLongJumpTiles(
                new MovementGrid(7, 7, Set.of(), Map.of()),
                JumpProfile.basic(new GridCoord(3, 3), 2, 0)
        )));
        result.put("long_wallrunner_blocked_path", coords(Movement.legalLongJumpTiles(
                new MovementGrid(5, 1, Set.of(new GridCoord(1, 0)), Map.of()),
                new JumpProfile(
                        new GridCoord(0, 0), 1, 0,
                        false, false, false, false, false, 1
                )
        )));
        result.put("long_no_wallrunner_blocked_path", coords(Movement.legalLongJumpTiles(
                new MovementGrid(4, 1, Set.of(new GridCoord(1, 0)), Map.of()),
                JumpProfile.basic(new GridCoord(0, 0), 2, 0)
        )));
        result.put("long_water_no_swim", coords(Movement.legalLongJumpTiles(
                new MovementGrid(4, 1, Set.of(), Map.of(new GridCoord(2, 0), "Water")),
                JumpProfile.basic(new GridCoord(0, 0), 3, 0)
        )));
        result.put("long_water_swim", coords(Movement.legalLongJumpTiles(
                new MovementGrid(4, 1, Set.of(), Map.of(new GridCoord(2, 0), "Water")),
                new JumpProfile(
                        new GridCoord(0, 0), 3, 0,
                        false, true, false, false, false, 0
                )
        )));
        result.put("long_burrow_intermediate_block", coords(Movement.legalLongJumpTiles(
                new MovementGrid(4, 1, Set.of(new GridCoord(1, 0)), Map.of()),
                new JumpProfile(
                        new GridCoord(0, 0), 2, 0,
                        false, false, true, false, false, 0
                )
        )));
        result.put("long_fit_reject", coords(Movement.legalLongJumpTiles(
                new MovementGrid(3, 1, Set.of(), Map.of()),
                JumpProfile.basic(new GridCoord(0, 0), 2, 0),
                coord -> !coord.equals(new GridCoord(1, 0))
        )));
        result.put("high_basic_2", coords(Movement.legalHighJumpTiles(
                new MovementGrid(7, 7, Set.of(), Map.of()),
                JumpProfile.basic(new GridCoord(3, 3), 0, 2)
        )));
        result.put("high_water_no_swim", coords(Movement.legalHighJumpTiles(
                new MovementGrid(3, 1, Set.of(), Map.of(new GridCoord(1, 0), "Water")),
                JumpProfile.basic(new GridCoord(0, 0), 0, 1)
        )));
        result.put("high_blocked_destination", coords(Movement.legalHighJumpTiles(
                new MovementGrid(3, 1, Set.of(new GridCoord(1, 0)), Map.of()),
                JumpProfile.basic(new GridCoord(0, 0), 0, 1)
        )));

        return result;
    }

    private static Map<String, String> readFixtures(Path path) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid oracle fixture line: " + line);
            }
            result.put(parts[0], parts[1]);
        }
        return result;
    }

    private static String coords(Set<GridCoord> value) {
        return value.stream()
                .sorted(Comparator.comparingInt(GridCoord::x).thenComparingInt(GridCoord::y))
                .map(coord -> coord.x() + "," + coord.y())
                .collect(Collectors.joining(";"));
    }
}
