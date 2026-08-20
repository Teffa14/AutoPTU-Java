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

final class PokemonInitiativeEntryResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonInitiativeEntryFixtures() throws IOException {
        String oraclePath = System.getProperty("autoptu.pokemon.initiative.entry.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\tactor_id\ttrainer_id\tresolved_speed\ttrainer_modifier\tbashed\ttailwind\tround\ttemporary_effects\tzero_until_turn\texpected_speed\texpected_roll\texpected_total",
                lines.getFirst()
        );
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            InitiativeEntry actual = PokemonInitiativeEntryResolution.resolve(
                    parts[1],
                    parts[2],
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    "1".equals(parts[5]),
                    "1".equals(parts[6]),
                    Integer.parseInt(parts[7]),
                    parseEffects(parts[8]),
                    0,
                    "1".equals(parts[9])
            );
            assertEquals(Integer.parseInt(parts[10]), actual.speed(), name + " speed");
            assertEquals(Integer.parseInt(parts[11]), actual.roll(), name + " roll");
            assertEquals(Integer.parseInt(parts[12]), actual.total(), name + " total");
            assertEquals(parts[1], actual.actorId(), name + " actor");
            assertEquals(parts[2], actual.trainerId(), name + " trainer");
            assertEquals(Integer.parseInt(parts[4]), actual.trainerModifier(), name + " trainer modifier");
        }
    }

    @Test
    void preservesAdditionalResolvedInitiativeBonusAsASeparateParitySeam() {
        InitiativeEntry entry = PokemonInitiativeEntryResolution.resolve(
                "p1", "t1", 20, 3, false, true, 4, List.of(), 7, false
        );
        assertEquals(35, entry.total());
    }

    private static List<TemporaryEffectEntry> parseEffects(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        ArrayList<TemporaryEffectEntry> result = new ArrayList<>();
        for (String token : encoded.split(";")) {
            String[] parts = token.split(":", -1);
            String name = parts[0];
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            if (parts.length > 1 && !parts[1].isBlank()) payload.put("amount", scalar(parts[1]));
            if (parts.length > 2 && !parts[2].isBlank()) payload.put("expires_round", scalar(parts[2]));
            result.add(new TemporaryEffectEntry(name, Map.copyOf(payload)));
        }
        return List.copyOf(result);
    }

    private static Object scalar(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }
}
