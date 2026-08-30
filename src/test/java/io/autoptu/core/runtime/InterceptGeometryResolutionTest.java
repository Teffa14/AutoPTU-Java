package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InterceptGeometryResolutionTest {
    @Test
    void oracleContractMatchesPinnedPython() throws IOException {
        String fixturePath = System.getProperty("autoptu.intercept.geometry.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());
        Map<String, Integer> fixture = readFixture(Path.of(fixturePath));
        assertEquals(1, fixture.get("candidate_sort_uses_footprint_distance"));
        assertEquals(1, fixture.get("candidate_sort_targets_medium_anchor"));
        assertEquals(1, fixture.get("candidate_selection_uses_first_sorted_interceptor"));
        assertEquals(1, fixture.get("attack_line_uses_line_cells"));
        assertEquals(1, fixture.get("off_line_uses_legal_shift_tiles"));
        assertEquals(1, fixture.get("no_legal_line_tile_aborts_intercept"));
        assertEquals(1, fixture.get("no_legal_line_tile_aborts_before_check"));
        assertEquals(1, fixture.get("line_tile_sort_uses_footprint_distance"));
        assertEquals(1, fixture.get("line_tile_sort_targets_medium_anchor"));
        assertEquals(1, fixture.get("check_distance_uses_footprint_distance"));
        assertEquals(1, fixture.get("check_distance_targets_medium_anchor"));
        assertEquals(1, fixture.get("check_distance_floor_one"));
        assertEquals(1, fixture.get("line_tile_recomputes_check_distance"));
    }

    @Test
    void ordersCandidatesByFootprintDistanceAndKeepsStableTies() {
        GridCoord target = new GridCoord(5, 5);
        var far = new InterceptGeometryResolution.Candidate("far", new GridCoord(1, 1), "Medium");
        var nearLarge = new InterceptGeometryResolution.Candidate("large", new GridCoord(3, 4), "Large");
        var tieA = new InterceptGeometryResolution.Candidate("tie-a", new GridCoord(4, 3), "Medium");
        var tieB = new InterceptGeometryResolution.Candidate("tie-b", new GridCoord(3, 4), "Medium");

        List<String> ids = InterceptGeometryResolution.orderCandidates(
                List.of(far, tieA, tieB, nearLarge), target
        ).stream().map(InterceptGeometryResolution.Candidate::combatantId).toList();

        assertEquals("large", ids.get(0));
        assertEquals(List.of("tie-a", "tie-b"), ids.subList(1, 3));
        assertEquals("far", ids.get(3));
    }

    @Test
    void keepsCurrentPositionWhenInterceptorAlreadyOccupiesAttackLine() {
        var interceptor = new InterceptGeometryResolution.Candidate("i", new GridCoord(2, 2), "Medium");
        GridCoord selected = InterceptGeometryResolution.nearestReachableAttackLineTile(
                interceptor,
                List.of(new GridCoord(1, 1), new GridCoord(2, 2), new GridCoord(3, 3)),
                List.of()
        );
        assertEquals(new GridCoord(2, 2), selected);
    }

    @Test
    void choosesNearestReachableLineTileAndPreservesStableLineOrderOnTie() {
        var interceptor = new InterceptGeometryResolution.Candidate("i", new GridCoord(1, 3), "Medium");
        GridCoord first = new GridCoord(2, 2);
        GridCoord second = new GridCoord(2, 4);
        GridCoord selected = InterceptGeometryResolution.nearestReachableAttackLineTile(
                interceptor,
                List.of(first, second, new GridCoord(3, 3)),
                List.of(first, second)
        );
        assertEquals(first, selected);
    }

    @Test
    void returnsNullWhenNoAttackLineTileIsReachable() {
        var interceptor = new InterceptGeometryResolution.Candidate("i", new GridCoord(0, 0), "Medium");
        assertNull(InterceptGeometryResolution.nearestReachableAttackLineTile(
                interceptor,
                List.of(new GridCoord(2, 2), new GridCoord(3, 3)),
                List.of(new GridCoord(1, 0))
        ));
    }

    @Test
    void checkDistanceUsesFootprintGeometryAndFloorsOverlapToOne() {
        var medium = new InterceptGeometryResolution.Candidate("i", new GridCoord(1, 1), "Medium");
        assertEquals(3, InterceptGeometryResolution.checkDistance(medium, new GridCoord(4, 1)));
        assertEquals(1, InterceptGeometryResolution.checkDistance(medium, new GridCoord(1, 1)));

        var large = new InterceptGeometryResolution.Candidate("large", new GridCoord(1, 1), "Large");
        assertEquals(1, InterceptGeometryResolution.checkDistance(large, new GridCoord(3, 1)));
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank() || line.startsWith("key\t")) continue;
            String[] parts = line.split("\\t", -1);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
