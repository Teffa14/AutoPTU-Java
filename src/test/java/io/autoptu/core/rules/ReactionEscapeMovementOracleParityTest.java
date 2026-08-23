package io.autoptu.core.rules;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ReactionEscapeMovementOracleParityTest {
    @Test
    void choosesFarthestSafeTileAndPreservesFirstTie() {
        GridCoord origin = new GridCoord(1, 1);
        List<GridCoord> reachable = List.of(
                origin,
                new GridCoord(2, 1),
                new GridCoord(3, 1),
                new GridCoord(1, 3)
        );
        List<GridCoord> threatened = List.of(origin, new GridCoord(2, 1));

        assertEquals(
                Optional.of(new GridCoord(3, 1)),
                ReactionEscapeMovementResolution.chooseDestination(origin, reachable, threatened)
        );
    }

    @Test
    void optionalDistanceCapMatchesPerceptionErrataPattern() {
        GridCoord origin = new GridCoord(1, 1);
        List<GridCoord> reachable = List.of(
                origin,
                new GridCoord(3, 1),
                new GridCoord(2, 1),
                new GridCoord(1, 2)
        );

        assertEquals(
                Optional.of(new GridCoord(2, 1)),
                ReactionEscapeMovementResolution.chooseDestination(
                        origin,
                        reachable,
                        List.of(origin),
                        1
                )
        );
    }

    @Test
    void matchesPinnedPythonHooksWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_REACTION_ESCAPE_MOVEMENT_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertTrue(lines.size() > 1, "reaction escape oracle fixture must contain cases");
        assertEquals(
                "case\torigin\treachable\tthreatened\tmax_distance\tdestination",
                lines.getFirst()
        );

        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(6, parts.length, "malformed fixture row: " + line);
            GridCoord origin = parseCoord(parts[1]);
            List<GridCoord> reachable = parseCoords(parts[2]);
            List<GridCoord> threatened = parseCoords(parts[3]);
            Integer maxDistance = parts[4].isEmpty() ? null : Integer.parseInt(parts[4]);
            Optional<GridCoord> actual = ReactionEscapeMovementResolution.chooseDestination(
                    origin,
                    reachable,
                    threatened,
                    maxDistance
            );
            Optional<GridCoord> expected = parts[5].isEmpty()
                    ? Optional.empty()
                    : Optional.of(parseCoord(parts[5]));
            assertEquals(expected, actual, parts[0]);
        }
    }

    private static GridCoord parseCoord(String encoded) {
        String[] parts = encoded.split(",", -1);
        return new GridCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    private static List<GridCoord> parseCoords(String encoded) {
        if (encoded.isEmpty()) return List.of();
        List<GridCoord> coords = new ArrayList<>();
        for (String item : encoded.split("\\|", -1)) {
            coords.add(parseCoord(item));
        }
        return coords;
    }
}
