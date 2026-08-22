package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusStackOracleParityTest {
    @Test
    void storageCanReproducePinnedTrainerFeatureStatusOutcomes() throws IOException {
        Path fixture = Path.of("build/oracle/status-stack.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = readFixture(fixture);

        LinkedHashMap<String, StatusStateStore> cases = new LinkedHashMap<>();

        StatusStateStore applyNew = new StatusStateStore();
        applyNew.append("ally", new StatusEntry("Poisoned", Map.of(
                "source", "trainer_feature:stack-test", "remaining", 3, "duration", 3)));
        cases.put("apply_new_duration", applyNew);

        StatusStateStore refresh = new StatusStateStore();
        refresh.replace("ally", List.of(
                new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 2, "duration", 2)),
                new StatusEntry("Poisoned", Map.of("source", "move:b", "remaining", 1, "duration", 1))));
        refresh.put("ally", new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 5, "duration", 5)));
        cases.put("refresh_first_shorter_duration", refresh);

        StatusStateStore longer = new StatusStateStore();
        longer.replace("ally", List.of(new StatusEntry("Poisoned", Map.of(
                "source", "move:a", "remaining", 7, "duration", 7))));
        cases.put("existing_longer_no_change", longer);

        StatusStateStore stacked = new StatusStateStore();
        stacked.replace("ally", List.of(
                new StatusEntry("Poisoned", Map.of("source", "move:a", "remaining", 2, "duration", 2)),
                new StatusEntry("Burned")));
        stacked.append("ally", new StatusEntry("Poisoned", Map.of(
                "source", "trainer_feature:stack-test", "remaining", 4, "duration", 4)));
        cases.put("stack_appends_duplicate", stacked);

        StatusStateStore zeroDuration = new StatusStateStore();
        zeroDuration.replace("ally", List.of(new StatusEntry("Poisoned", Map.of(
                "source", "move:a", "remaining", 2, "duration", 2))));
        cases.put("zero_duration_existing_no_change", zeroDuration);

        StatusStateStore removeNamed = new StatusStateStore();
        removeNamed.replace("ally", List.of(
                new StatusEntry("Poisoned", Map.of("source", "move:a")),
                new StatusEntry("Burned"),
                new StatusEntry("Poisoned", Map.of("source", "move:b"))));
        removeNamed.removeAll("ally", "Poisoned");
        cases.put("remove_named_removes_all_duplicates", removeNamed);

        StatusStateStore removeAll = new StatusStateStore();
        removeAll.replace("ally", List.of(
                new StatusEntry("Poisoned", Map.of("source", "move:a")),
                new StatusEntry("Burned"),
                new StatusEntry("Poisoned", Map.of("source", "move:b"))));
        removeAll.clear("ally");
        cases.put("remove_all_clears_every_entry", removeAll);

        assertEquals(expected.keySet(), cases.keySet());
        for (Map.Entry<String, StatusStateStore> entry : cases.entrySet()) {
            Expected exp = expected.get(entry.getKey());
            assertEquals(exp.snapshot(), snapshot(entry.getValue()), entry.getKey() + " status snapshot");
        }

        assertEquals(true, expected.get("apply_new_duration").applied());
        assertEquals(true, expected.get("refresh_first_shorter_duration").applied());
        assertEquals(false, expected.get("existing_longer_no_change").applied());
        assertEquals(true, expected.get("stack_appends_duplicate").applied());
        assertEquals(false, expected.get("zero_duration_existing_no_change").applied());
        assertEquals(true, expected.get("remove_named_removes_all_duplicates").applied());
        assertEquals(true, expected.get("remove_all_clears_every_entry").applied());
    }

    private static String snapshot(StatusStateStore store) {
        return store.entries("ally").stream().map(StatusStackOracleParityTest::serialize).collect(java.util.stream.Collectors.joining(";"));
    }

    private static String serialize(StatusEntry entry) {
        return String.join("|",
                entry.name(),
                entry.stringPayload("source").orElse(""),
                entry.intPayload("remaining").map(String::valueOf).orElse(""),
                entry.intPayload("duration").map(String::valueOf).orElse(""));
    }

    private static Map<String, Expected> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Expected> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            out.put(parts[0], new Expected(
                    "1".equals(parts[1]),
                    parts[2],
                    parts[3],
                    parts[4],
                    parts[5],
                    parts[6],
                    parts[7]));
        }
        return out;
    }

    private record Expected(
            boolean applied,
            String effectType,
            String targets,
            String snapshot,
            String detailStatus,
            String detailDuration,
            String removed
    ) {}
}
