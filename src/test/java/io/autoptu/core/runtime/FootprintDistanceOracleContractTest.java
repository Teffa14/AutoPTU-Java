package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Freezes the footprint-sensitive metric behind Shadow Tag's candidate-step distance guard. */
class FootprintDistanceOracleContractTest {
    @Test
    void freezesPinnedFootprintDistanceMetric() throws IOException {
        String fixture = System.getenv("AUTOPTU_FOOTPRINT_DISTANCE_ORACLE");
        if (fixture == null || fixture.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixture));
        assertFalse(lines.isEmpty());
        assertEquals("path\tsymbol\trole\tline\tcontract", lines.getFirst());

        String joined = String.join("\n", lines);
        assertTrue(joined.contains("_FOOTPRINT_BY_SIZE\tmapping"));
        assertTrue(joined.contains("'small': 1"));
        assertTrue(joined.contains("'medium': 1"));
        assertTrue(joined.contains("'large': 2"));
        assertTrue(joined.contains("'huge': 3"));
        assertTrue(joined.contains("'gigantic': 4"));
        assertTrue(joined.contains("footprint_distance\timplementation"));
        assertTrue(joined.contains("footprint_tiles(a_anchor, a_size, grid)"));
        assertTrue(joined.contains("footprint_tiles(b_anchor, b_size, grid)"));
        assertTrue(joined.contains("chebyshev_distance(a_tile, b_tile) for a_tile in a_tiles for b_tile in b_tiles"));
        assertTrue(joined.contains("chebyshev_distance\timplementation"));
        assertTrue(joined.contains("max(abs(a[0] - b[0]), abs(a[1] - b[1]))"));
    }
}
