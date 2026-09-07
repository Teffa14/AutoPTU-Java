package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RoundStartedEvent;
import io.autoptu.core.event.TurnStartedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.InitiativeOrderAssemblyResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundStartedEventOracleParityTest {
    @Test
    @SuppressWarnings("deprecation")
    void rolloverEmitsPinnedPythonRoundStartPayloadBeforeFirstTurn() throws IOException {
        Path fixture = Path.of("build/oracle/round-start-event.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        Fixture expected = parseFixture(Files.readAllLines(fixture));

        RuntimeCombatantState alpha = combatant("alpha", 31, 40, List.of("Static", "Sprint"));
        RuntimeCombatantState bench = combatant("bench", 22, 35, List.of("Run Away"));
        BattleRuntimeState state = new BattleRuntimeState(
                new MovementGrid(5, 5, Set.of(), Map.of()),
                List.of(alpha, bench),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(
                        "alpha", new CombatantAffiliationState("team-a", true),
                        "bench", new CombatantAffiliationState("team-a", false)
                )
        );
        state.replaceStatusEntries("alpha", List.of(
                new StatusEntry("Burned"),
                new StatusEntry("Confused"),
                new StatusEntry("Burned")
        ));
        state.syncEnvironmentFromRuntime(new BattleEnvironmentState(
                expected.weather(), "", Set.of(), Map.of()
        ));
        state.putTrainer(new TrainerRuntimeState(
                "trainer-a", List.of(), 3, 0, Map.of(), 30, "team-a"
        ));

        List<InitiativeEntry> entries = expected.initiative().stream()
                .map(entry -> new InitiativeEntry(
                        entry.actor(), entry.controller(), entry.speed(), entry.trainerModifier(), entry.roll(), entry.total()
                ))
                .toList();
        InitiativeAssemblyInstaller.install(state, new InitiativeOrderAssemblyResult(entries, Map.of()));

        BattleRoundController rounds = new BattleRoundController(state, expected.round() - 1);
        rounds.setInitiativeCursor(entries.size());
        InitiativeTurnAdvanceResult result = rounds.advanceInitiativeTurnWithRollover(
                (runtime, round) -> entries.stream().map(InitiativeEntry::actorId).toList()
        );

        List<BattleEvent> events = result.events();
        int roundStartIndex = indexOf(events, RoundStartedEvent.class);
        int turnStartIndex = indexOf(events, TurnStartedEvent.class);
        assertTrue(roundStartIndex >= 0, "round_start event emitted");
        assertTrue(turnStartIndex > roundStartIndex, "round_start precedes first TURN_START");

        RoundStartedEvent actual = (RoundStartedEvent) events.get(roundStartIndex);
        assertEquals(expected.round(), actual.round());
        assertEquals(expected.weather(), actual.weather());
        assertEquals(expected.initiative(), actual.initiative());
        assertEquals(expected.combatants(), actual.initialStates());
        assertTrue(result.hasActor());
        assertEquals("alpha", result.actorId());
        assertEquals(0, state.initiativeProgress().cursor());
    }

    private static RuntimeCombatantState combatant(String id, int hp, int maxHp, List<String> abilities) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(new GridCoord(1, 1), 4),
                hp,
                maxHp,
                new ActionBudget(),
                null,
                null,
                0,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                abilities
        );
    }

    private static int indexOf(List<BattleEvent> events, Class<? extends BattleEvent> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) return i;
        }
        return -1;
    }

    private static Fixture parseFixture(List<String> lines) {
        int round = -1;
        String weather = "";
        ArrayList<RoundStartedEvent.InitiativeSnapshot> initiative = new ArrayList<>();
        ArrayList<RoundStartedEvent.CombatantSnapshot> combatants = new ArrayList<>();

        for (String line : lines) {
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            switch (parts[0]) {
                case "ROUND_START" -> {
                    round = Integer.parseInt(parts[1]);
                    weather = parts[2];
                }
                case "INITIATIVE" -> initiative.add(new RoundStartedEvent.InitiativeSnapshot(
                        parts[1], parts[2], Integer.parseInt(parts[3]), Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]), Integer.parseInt(parts[6])
                ));
                case "COMBATANT" -> combatants.add(new RoundStartedEvent.CombatantSnapshot(
                        parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]),
                        csv(parts[4]), csv(parts[5]), Boolean.parseBoolean(parts[6])
                ));
                default -> throw new IllegalArgumentException("unknown fixture row: " + parts[0]);
            }
        }
        return new Fixture(round, weather, List.copyOf(initiative), List.copyOf(combatants));
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.asList(value.split(","));
    }

    private record Fixture(
            int round,
            String weather,
            List<RoundStartedEvent.InitiativeSnapshot> initiative,
            List<RoundStartedEvent.CombatantSnapshot> combatants
    ) {}
}
