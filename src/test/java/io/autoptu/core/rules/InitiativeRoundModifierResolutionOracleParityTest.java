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

final class InitiativeRoundModifierResolutionOracleParityTest {
    @Test
    void matchesPinnedPythonRoundModifierFixtures() throws IOException {
        String oraclePath = System.getProperty("autoptu.initiative.round.modifier.oracle");
        if (oraclePath == null || oraclePath.isBlank()) {
            return;
        }

        List<String> lines = Files.readAllLines(Path.of(oraclePath));
        assertEquals(
                "name\tactor_id\ttrainer_id\tbase_total\tround\tinner_focus\ttemporary_effects\texpected_total\tremoved",
                lines.getFirst()
        );
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String name = parts[0];
            InitiativeEntry base = new InitiativeEntry(
                    parts[1],
                    parts[2],
                    10,
                    0,
                    0,
                    Integer.parseInt(parts[3])
            );
            List<String> abilities = "1".equals(parts[5])
                    ? List.of("Inner Focus [Errata]")
                    : List.of();
            InitiativeRoundModifierResult actual = InitiativeRoundModifierResolution.resolve(
                    base,
                    Integer.parseInt(parts[4]),
                    parseEffects(parts[6]),
                    abilities
            );

            assertEquals(Integer.parseInt(parts[7]), actual.entry().total(), name + " total");
            assertEquals(parseRemoved(parts[8]), actual.temporaryEffectsToClear(), name + " cleanup");
            assertEquals(base.actorId(), actual.entry().actorId(), name + " actor");
            assertEquals(base.trainerId(), actual.entry().trainerId(), name + " trainer");
            assertEquals(base.speed(), actual.entry().speed(), name + " speed");
            assertEquals(base.trainerModifier(), actual.entry().trainerModifier(), name + " trainer modifier");
            assertEquals(base.roll(), actual.entry().roll(), name + " roll");
        }
    }

    @Test
    void matchesOnlyTheExactInnerFocusErrataVariant() {
        InitiativeEntry base = new InitiativeEntry("p1", "t1", 10, 0, 0, 20);
        TemporaryEffectEntry penalty = new TemporaryEffectEntry(
                "initiative_penalty",
                Map.of("amount", -4, "source_id", "enemy", "expires_round", 2)
        );

        assertEquals(16, InitiativeRoundModifierResolution.resolve(
                base, 2, List.of(penalty), List.of("Inner Focus")
        ).entry().total());
        assertEquals(20, InitiativeRoundModifierResolution.resolve(
                base, 2, List.of(penalty), List.of("Inner Focus [Errata]")
        ).entry().total());
    }

    private static List<TemporaryEffectEntry> parseEffects(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        ArrayList<TemporaryEffectEntry> result = new ArrayList<>();
        for (String token : encoded.split(";")) {
            String[] fields = token.split("\\|", -1);
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            for (int index = 1; index < fields.length; index++) {
                String[] pair = fields[index].split("=", 2);
                if (pair.length == 2) payload.put(pair[0], scalar(pair[1]));
            }
            result.add(new TemporaryEffectEntry(fields[0], Map.copyOf(payload)));
        }
        return List.copyOf(result);
    }

    private static List<String> parseRemoved(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        return List.of(encoded.split(","));
    }

    private static Object scalar(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }
}
