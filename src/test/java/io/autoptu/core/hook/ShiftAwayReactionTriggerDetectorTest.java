package io.autoptu.core.hook;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ShiftAwayReactionTriggerDetectorTest {
    @Test
    void adjacentFoeLeavingAdjacencyProducesTrigger() {
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY),
                ShiftAwayReactionTriggerDetector.detect(
                        new GridCoord(1, 1), "Medium",
                        new GridCoord(2, 1), new GridCoord(3, 1), "Medium")
        );
    }

    @Test
    void remainingAdjacentDoesNotProduceTrigger() {
        assertEquals(
                Optional.empty(),
                ShiftAwayReactionTriggerDetector.detect(
                        new GridCoord(1, 1), "Medium",
                        new GridCoord(2, 1), new GridCoord(2, 2), "Medium")
        );
    }

    @Test
    void movementThatStartsNonAdjacentDoesNotProduceTrigger() {
        assertEquals(
                Optional.empty(),
                ShiftAwayReactionTriggerDetector.detect(
                        new GridCoord(1, 1), "Medium",
                        new GridCoord(3, 1), new GridCoord(4, 1), "Medium")
        );
    }

    @Test
    void largeFootprintsUseCanonicalFootprintAdjacency() {
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY),
                ShiftAwayReactionTriggerDetector.detect(
                        new GridCoord(2, 2), "Large",
                        new GridCoord(4, 2), new GridCoord(5, 2), "Medium")
        );
    }

    @Test
    void matchesPinnedPythonFootprintTransitionsWhenFixtureIsProvided() throws IOException {
        String fixturePath = System.getenv("AUTOPTU_SHIFT_AWAY_TRIGGER_ORACLE");
        if (fixturePath == null || fixturePath.isBlank()) return;

        List<String> lines = Files.readAllLines(Path.of(fixturePath));
        assertEquals("case\treactor\treactor_size\tbefore\tafter\tfoe_size\ttriggered", lines.getFirst());
        for (String line : lines.subList(1, lines.size())) {
            String[] parts = line.split("\\t", -1);
            assertEquals(7, parts.length, "malformed fixture row: " + line);
            boolean expected = Boolean.parseBoolean(parts[6]);
            boolean actual = ShiftAwayReactionTriggerDetector.detect(
                    parseCoord(parts[1]), parts[2], parseCoord(parts[3]), parseCoord(parts[4]), parts[5]
            ).isPresent();
            assertEquals(expected, actual, parts[0]);
        }
    }

    private static GridCoord parseCoord(String encoded) {
        String[] parts = encoded.split(",", -1);
        return new GridCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }
}
