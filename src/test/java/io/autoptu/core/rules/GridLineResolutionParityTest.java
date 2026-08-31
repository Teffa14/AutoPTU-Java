package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GridLineResolutionParityTest {
    @Test
    void matchesPinnedPythonLineCellsFixture() throws IOException {
        String oraclePath = System.getProperty("autoptu.grid.line.oracle");
        Assumptions.assumeTrue(oraclePath != null && !oraclePath.isBlank());

        List<String> rows = Files.readAllLines(Path.of(oraclePath));
        assertFalse(rows.isEmpty(), "grid line oracle fixture must not be empty");
        assertEquals("case\torigin_x\torigin_y\ttarget_x\ttarget_y\tcells", rows.get(0));

        for (String row : rows.subList(1, rows.size())) {
            if (row.isBlank()) continue;
            String[] columns = row.split("\t", -1);
            assertEquals(6, columns.length, "invalid fixture row: " + row);
            GridCoord origin = new GridCoord(Integer.parseInt(columns[1]), Integer.parseInt(columns[2]));
            GridCoord target = new GridCoord(Integer.parseInt(columns[3]), Integer.parseInt(columns[4]));
            List<GridCoord> expected = columns[5].isBlank()
                    ? List.of()
                    : Arrays.stream(columns[5].split(";"))
                            .map(GridLineResolutionParityTest::coord)
                            .toList();
            assertEquals(expected, GridLineResolution.cells(origin, target), columns[0]);
        }
    }

    private static GridCoord coord(String encoded) {
        String[] parts = encoded.split(",", -1);
        return new GridCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
