package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelayedHitOracleParityTest {
    @Test
    void schedulingAndDuePartitionMatchPinnedPythonBehavior() throws IOException {
        String fixturePath = System.getProperty("autoptu.delayed.hit.oracle");
        Assumptions.assumeTrue(fixturePath != null && !fixturePath.isBlank());

        DelayedHitQueue queue = new DelayedHitQueue();
        LinkedHashMap<String, Expected> expectedByMove = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(fixturePath))) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String scenario = parts[0];
            String attackerId = parts[1];
            String moveId = parts[2];
            String targetId = parts[3].isBlank() ? null : parts[3];
            GridCoord targetPosition = parts[4].isBlank()
                    ? null
                    : new GridCoord(Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
            int triggerRound = Integer.parseInt(parts[6]);
            String effect = parts[7];
            String outcome = parts[8];
            int outcomeIndex = Integer.parseInt(parts[9]);

            queue.schedule(attackerId, moveId, targetId, targetPosition, triggerRound, effect);
            expectedByMove.put(moveId, new Expected(scenario, outcome, outcomeIndex));
        }

        DelayedHitBatch batch = queue.takeDue(3);
        assertEquals(2, batch.due().size());
        assertEquals(1, batch.remaining().size());
        assertEquals(batch.remaining(), queue.entriesInInsertionOrder());

        assertOutcome(batch.due(), expectedByMove, "due");
        assertOutcome(batch.remaining(), expectedByMove, "remaining");
    }

    @Test
    void queuePreservesInsertionOrderAndRejectsInvalidRound() {
        DelayedHitQueue queue = new DelayedHitQueue();
        queue.schedule("a", "first", "t", null, 2, "one");
        queue.schedule("b", "second", null, new GridCoord(4, 5), 2, "two");

        assertEquals(List.of("first", "second"), queue.entriesInInsertionOrder().stream().map(DelayedHitEntry::moveId).toList());
        assertThrows(IllegalArgumentException.class, () -> queue.takeDue(-1));
    }

    private static void assertOutcome(
            List<DelayedHitEntry> actual,
            Map<String, Expected> expectedByMove,
            String outcome
    ) {
        ArrayList<DelayedHitEntry> expectedOrder = new ArrayList<>(actual);
        expectedOrder.sort((left, right) -> Integer.compare(
                expectedByMove.get(left.moveId()).index(),
                expectedByMove.get(right.moveId()).index()
        ));
        assertEquals(expectedOrder, actual, outcome + " insertion order");
        for (int index = 0; index < actual.size(); index++) {
            DelayedHitEntry entry = actual.get(index);
            Expected expected = expectedByMove.get(entry.moveId());
            assertEquals(outcome, expected.outcome(), expected.scenario());
            assertEquals(index, expected.index(), expected.scenario());
        }
    }

    private record Expected(String scenario, String outcome, int index) {}
}
