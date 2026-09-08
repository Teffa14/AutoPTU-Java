package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaTrapEffectMutationOracleParityTest {
    @Test
    void orderedEventsAndFinalStatusMetadataMatchPinnedPython() throws IOException {
        Path fixture = Path.of("build/oracle/arena-trap-targeting.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, String> expected = parse(Files.readAllLines(fixture));
        List<String> targets = expected.getOrDefault("SLOWED", "").isBlank()
                ? List.of()
                : Arrays.asList(expected.get("SLOWED").split(","));

        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(12, 12, Set.of(), Map.of()),
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(combatant("holder", 2, 2)),
                        targets.stream().map(id -> combatant(id, 2, 3))
                ).toList()
        );

        List<BattleEvent> events = StatusEffectMutationExecutor.apply(
                state,
                ArenaTrapEffectPlan.statusInstructionsForTargets("holder", targets)
        );

        String actualEvents = events.stream()
                .map(event -> eventRow((AbilityEvent) event))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        String actualStatuses = targets.stream()
                .map(target -> statusRow(state, target))
                .reduce((left, right) -> left + ";" + right)
                .orElse("");

        assertEquals(expected.get("EVENTS"), actualEvents);
        assertEquals(expected.get("STATUSES"), actualStatuses);
    }

    private static String eventRow(AbilityEvent event) {
        return String.join("|",
                event.actorId(),
                event.target(),
                event.ability(),
                event.effect(),
                event.description()
        );
    }

    private static String statusRow(BattleRuntimeState state, String targetId) {
        StatusEntry status = state.statusEntry(targetId, "Slowed").orElseThrow();
        return String.join("|",
                targetId,
                Integer.toString(status.intPayload("remaining").orElseThrow()),
                status.stringPayload("source").orElseThrow(),
                status.stringPayload("source_id").orElseThrow()
        );
    }

    private static RuntimeCombatantState combatant(String id, int x, int y) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(x, y), 4),
                20,
                20,
                new ActionBudget()
        );
    }

    private static Map<String, String> parse(List<String> lines) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", 2);
            if (parts.length != 2) throw new IllegalArgumentException("invalid fixture row: " + line);
            result.put(parts[0], parts[1]);
        }
        return Map.copyOf(result);
    }
}
