package io.autoptu.core.runtime;

import io.autoptu.core.model.InitiativeEntry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReplacementInitiativeInsertionOracleParityTest {
    @Test
    void insertionOrderAndCursorMatchPinnedPythonCases() throws IOException {
        Path fixture = Path.of("build/oracle/replacement-initiative.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        Map<String, OracleCase> oracleCases = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (!line.startsWith("CASE\t")) continue;
            String[] parts = line.split("\\t", -1);
            oracleCases.put(parts[1], new OracleCase(
                    parts[1],
                    actorIds(parts[2]),
                    Integer.parseInt(parts[3]),
                    actorIds(parts[4]),
                    Integer.parseInt(parts[5])
            ));
        }

        assertEquals(List.of(
                "duplicate_noop",
                "missing_entry_noop",
                "natural_future",
                "natural_past_no_immediate",
                "natural_past_immediate",
                "tie_break_roll",
                "empty_round"
        ), List.copyOf(oracleCases.keySet()));

        for (OracleCase oracle : oracleCases.values()) {
            Scenario scenario = scenario(oracle.name());
            InitiativeProgressState progress = new InitiativeProgressState();
            progress.replaceDetailedOrderFromLifecycle(scenario.beforeEntries());
            progress.setCursorFromLifecycle(oracle.beforeCursor());

            ReplacementInitiativeInsertion.apply(
                    progress,
                    scenario.candidate(),
                    scenario.allowImmediate()
            );

            assertEquals(oracle.beforeOrder(), scenario.beforeEntries().stream()
                    .map(InitiativeEntry::actorId).toList(), oracle.name() + " fixture input drift");
            assertEquals(oracle.afterOrder(), progress.orderedActorIds(), oracle.name() + " order");
            assertEquals(oracle.afterCursor(), progress.cursor(), oracle.name() + " cursor");
            assertEquals(progress.orderedActorIds(), progress.orderedEntries().stream()
                    .map(InitiativeEntry::actorId).toList(), oracle.name() + " detailed order");
        }
    }

    private static Scenario scenario(String name) {
        InitiativeEntry fast = entry("fast", 100, 10, 10);
        InitiativeEntry current = entry("current", 80, 10, 10);
        InitiativeEntry slow = entry("slow", 50, 10, 10);
        return switch (name) {
            case "duplicate_noop" -> new Scenario(
                    List.of(fast, entry("replacement", 90, 10, 10), current, slow),
                    entry("replacement", 90, 10, 10), false);
            case "missing_entry_noop" -> new Scenario(List.of(fast, current, slow), null, false);
            case "natural_future" -> new Scenario(
                    List.of(fast, current, slow), entry("replacement", 70, 10, 10), false);
            case "natural_past_no_immediate" -> new Scenario(
                    List.of(fast, current, slow), entry("replacement", 90, 10, 10), false);
            case "natural_past_immediate" -> new Scenario(
                    List.of(fast, current, slow), entry("replacement", 90, 10, 10), true);
            case "tie_break_roll" -> new Scenario(
                    List.of(fast, current, slow), entry("replacement", 80, 20, 10), true);
            case "empty_round" -> new Scenario(
                    List.of(), entry("replacement", 75, 10, 10), true);
            default -> throw new AssertionError("unknown replacement initiative oracle case: " + name);
        };
    }

    private static InitiativeEntry entry(String actor, int total, int roll, int speed) {
        return new InitiativeEntry(actor, "trainer-" + actor, speed, 0, roll, total);
    }

    private static List<String> actorIds(String csv) {
        return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(","));
    }

    private record OracleCase(
            String name,
            List<String> beforeOrder,
            int beforeCursor,
            List<String> afterOrder,
            int afterCursor
    ) {
    }

    private record Scenario(
            List<InitiativeEntry> beforeEntries,
            InitiativeEntry candidate,
            boolean allowImmediate
    ) {
    }
}
