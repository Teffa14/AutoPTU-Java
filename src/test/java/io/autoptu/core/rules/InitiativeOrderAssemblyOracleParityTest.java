package io.autoptu.core.rules;

import io.autoptu.core.model.InitiativeEntry;
import io.autoptu.core.runtime.TemporaryEffectEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class InitiativeOrderAssemblyOracleParityTest {
    @Test
    void matchesPinnedPythonBuildInitiativeOrderFixtures() throws IOException {
        String oraclePath = System.getProperty("autoptu.initiative.order.assembly.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\tround\ttrick_room\tleague\texpected_entries\tremoved",
                lines.getFirst()
        );
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            int round = Integer.parseInt(parts[1]);
            boolean trickRoom = "1".equals(parts[2]);
            boolean league = "1".equals(parts[3]);
            CaseInput input = inputFor(name);

            InitiativeOrderAssemblyResult actual = InitiativeOrderAssembly.resolve(
                    input.trainers(),
                    input.pokemon(),
                    round,
                    trickRoom,
                    league
            );

            assertEquals(parseEntries(parts[4]), normalize(actual.orderedEntries()), name + " order/totals");
            assertEquals(parseCleanup(parts[5]), actual.temporaryEffectFamiliesToClear(), name + " cleanup");
        }
    }

    @Test
    void rejectsDuplicateTrainerActorIds() {
        InitiativeEntry trainer = entry("t1", "t1", 10, 10);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> InitiativeOrderAssembly.resolve(List.of(trainer, trainer), List.of(), 1, false, false)
        );
    }

    private static CaseInput inputFor(String name) {
        return switch (name) {
            case "normal_mixed_order", "trick_room_mixed_order" -> new CaseInput(
                    List.of(entry("t1", "t1", 18, 18)),
                    List.of(
                            candidate(entry("p1", "t1", 30, 30)),
                            candidate(entry("p2", "t1", 20, 20))
                    )
            );
            case "trainer_registry_without_pokemon" -> new CaseInput(
                    List.of(entry("t1", "t1", 18, 18), entry("t2", "t2", 40, 40)),
                    List.of(candidate(entry("p1", "t1", 10, 10)))
            );
            case "league_trainers_stay_first" -> new CaseInput(
                    List.of(entry("t1", "t1", 5, 5), entry("t2", "t2", 50, 50)),
                    List.of(
                            candidate(entry("p1", "t1", 100, 100)),
                            candidate(entry("p2", "t2", 10, 10))
                    )
            );
            case "filters_ineligible_pokemon" -> new CaseInput(
                    List.of(),
                    List.of(
                            candidate(entry("p1", "t1", 20, 20)),
                            new InitiativePokemonCandidate(entry("p2", "t1", 90, 90), true, true, false, List.of(), List.of()),
                            new InitiativePokemonCandidate(entry("p3", "t1", 80, 80), false, false, false, List.of(), List.of()),
                            new InitiativePokemonCandidate(entry("p4", "t1", 70, 70), true, false, true, List.of(), List.of()),
                            new InitiativePokemonCandidate(null, true, false, false, List.of(), List.of())
                    )
            );
            case "round_modifiers_and_cleanup" -> new CaseInput(
                    List.of(),
                    List.of(
                            new InitiativePokemonCandidate(
                                    entry("p1", "t1", 20, 20),
                                    true,
                                    false,
                                    false,
                                    List.of(new TemporaryEffectEntry("rocket_initiative", Map.of("round", 3))),
                                    List.of()
                            ),
                            new InitiativePokemonCandidate(
                                    entry("p2", "t1", 25, 25),
                                    true,
                                    false,
                                    false,
                                    List.of(
                                            new TemporaryEffectEntry("initiative_penalty", Map.of(
                                                    "amount", -8,
                                                    "source_id", "enemy",
                                                    "expires_round", 2
                                            )),
                                            new TemporaryEffectEntry("initiative_penalty", Map.of(
                                                    "amount", 4,
                                                    "source_id", "enemy",
                                                    "expires_round", 3
                                            ))
                                    ),
                                    List.of()
                            )
                    )
            );
            default -> throw new IllegalArgumentException("unknown oracle case: " + name);
        };
    }

    private static InitiativePokemonCandidate candidate(InitiativeEntry entry) {
        return new InitiativePokemonCandidate(entry, true, false, false, List.of(), List.of());
    }

    private static InitiativeEntry entry(String actorId, String trainerId, int speed, int total) {
        return new InitiativeEntry(actorId, trainerId, speed, 0, 0, total);
    }

    private static List<String> normalize(List<InitiativeEntry> entries) {
        return entries.stream().map(entry -> entry.actorId() + "|" + entry.total()).toList();
    }

    private static List<String> parseEntries(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        return List.of(encoded.split(";"));
    }

    private static Map<String, List<String>> parseCleanup(String encoded) {
        if (encoded == null || encoded.isBlank()) return Map.of();
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        for (String actorToken : encoded.split(";")) {
            String[] parts = actorToken.split(":", 2);
            result.put(parts[0], parts.length < 2 || parts[1].isBlank()
                    ? List.of()
                    : List.of(parts[1].split(",")));
        }
        return Map.copyOf(result);
    }

    private record CaseInput(List<InitiativeEntry> trainers, List<InitiativePokemonCandidate> pokemon) {
    }
}
