package io.autoptu.core.oracle;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.GridState;
import io.autoptu.core.model.MoveSpec;
import io.autoptu.core.rules.Targeting;
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

class TargetingOracleFixtureTest {
    @Test
    void javaMatchesPinnedPythonTargetingOracle() throws IOException {
        String fixturePath = System.getProperty("autoptu.targeting.oracle", "").strip();
        Assumptions.assumeFalse(fixturePath.isEmpty(), "Python oracle fixture path not configured");

        Map<String, String> expected = readFixtures(Path.of(fixturePath));
        Map<String, String> actual = javaResults();
        assertEquals(expected.keySet(), actual.keySet(), "Python and Java fixture scenario sets differ");
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), "Targeting parity failed for scenario: " + name);
        }
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

    private static Map<String, String> javaResults() {
        Map<String, String> result = new LinkedHashMap<>();
        GridState grid10 = new GridState(10, 10);

        MoveSpec normalize = new MoveSpec(
                "Ranged, 1 Target", "Ranged", 6, 6, "Close Blast 3", 3, ""
        );
        result.put("normalize_target", Targeting.normalizedTargetKind(normalize));
        result.put("normalize_area", Targeting.normalizedAreaKind(normalize));
        result.put("chebyshev_1_1_to_5_3", Integer.toString(
                Targeting.chebyshevDistance(new GridCoord(1, 1), new GridCoord(5, 3))
        ));
        result.put("large_footprint", coords(Targeting.footprintTiles(new GridCoord(5, 5), "Large")));
        result.put("huge_footprint_count", Integer.toString(Targeting.footprintTiles(new GridCoord(5, 5), "Huge").size()));
        result.put("gigantic_footprint_count", Integer.toString(Targeting.footprintTiles(new GridCoord(5, 5), "Gigantic").size()));
        result.put("large_to_medium_distance", Integer.toString(Targeting.footprintDistance(
                new GridCoord(0, 0), "Large", new GridCoord(2, 0), "Medium"
        )));

        MoveSpec melee = move("Melee", 1, null, null);
        result.put("large_melee_at_2", bool(Targeting.isTargetInRange(
                new GridCoord(0, 0), new GridCoord(2, 0), melee, "Large", "Medium", grid10
        )));
        result.put("large_melee_at_3", bool(Targeting.isTargetInRange(
                new GridCoord(0, 0), new GridCoord(3, 0), melee, "Large", "Medium", grid10
        )));

        result.put("line_east_3", coords(Targeting.affectedTiles(
                grid10, new GridCoord(2, 2), new GridCoord(4, 2), move("Self", 0, "Line", 3)
        )));
        result.put("cone_east_2", coords(Targeting.affectedTiles(
                grid10, new GridCoord(2, 2), new GridCoord(4, 2), move("Self", 0, "Cone", 2)
        )));
        result.put("closeblast_east_2", coords(Targeting.affectedTiles(
                grid10, new GridCoord(2, 2), new GridCoord(3, 2), move("Self", 0, "CloseBlast", 2)
        )));
        result.put("blast_3_center_5_5", coords(Targeting.affectedTiles(
                grid10, new GridCoord(1, 1), new GridCoord(5, 5), move("Ranged", 6, "Blast", 3)
        )));
        result.put("burst_1_corner", coords(Targeting.affectedTiles(
                new GridState(2, 2), new GridCoord(0, 0), new GridCoord(0, 0), move("Ranged", 6, "Burst", 1)
        )));
        result.put("los_blocked", bool(Targeting.lineOfSightClear(
                grid10, new GridCoord(0, 0), new GridCoord(4, 2), Set.of(new GridCoord(2, 1))
        )));
        result.put("los_clear_other_cell", bool(Targeting.lineOfSightClear(
                grid10, new GridCoord(0, 0), new GridCoord(4, 2), Set.of(new GridCoord(2, 2))
        )));
        result.put("ranged_anchor_count", Integer.toString(Targeting.targetAnchorTiles(
                new GridState(5, 5), new GridCoord(2, 2), move("Ranged", 2, null, null)
        ).size()));
        result.put("melee_anchor_count", Integer.toString(Targeting.targetAnchorTiles(
                new GridState(5, 5), new GridCoord(2, 2), move("Melee", 1, null, null)
        ).size()));

        return result;
    }

    private static MoveSpec move(String targetKind, Integer targetRange, String areaKind, Integer areaValue) {
        return new MoveSpec(
                targetKind,
                targetKind,
                targetRange,
                targetRange,
                areaKind,
                areaValue,
                targetKind
        );
    }

    private static String coords(Set<GridCoord> value) {
        return value.stream()
                .sorted(Comparator.comparingInt(GridCoord::x).thenComparingInt(GridCoord::y))
                .map(coord -> coord.x() + "," + coord.y())
                .collect(Collectors.joining(";"));
    }

    private static String bool(boolean value) {
        return value ? "true" : "false";
    }
}
