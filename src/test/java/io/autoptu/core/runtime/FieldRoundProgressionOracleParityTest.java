package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.FieldEffectEndedEvent;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldRoundProgressionOracleParityTest {
    @Test
    void javaProgressionMatchesPythonTerrainZoneRoomOracle() throws IOException {
        Path fixture = Path.of("build/oracle/field-round-progression.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        List<String> lines = Files.readAllLines(fixture);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] parts = lines.get(i).split("\\t", -1);
            String scenario = parts[0];
            int round = Integer.parseInt(parts[1]);
            FieldEffectEntry terrain = parseOne(FieldEffectKind.TERRAIN, parts[2]).orElse(null);
            List<FieldEffectEntry> zones = parseMany(FieldEffectKind.ZONE, parts[3]);
            List<FieldEffectEntry> rooms = parseMany(FieldEffectKind.ROOM, parts[4]);

            FieldRoundProgressionResult result = FieldRoundProgression.advance(round, terrain, zones, rooms);

            assertEquals(parts[5], encodeOptional(result.terrain()), scenario + " terrain");
            assertEquals(parts[6], encodeMany(result.zones()), scenario + " zones");
            assertEquals(parts[7], encodeMany(result.rooms()), scenario + " rooms");
            assertEquals(parts[8], encodeEvents(result.events()), scenario + " events");

            boolean cleanup = result.statusCleanups().stream().anyMatch(request ->
                    request.statusNames().contains("wondered") && request.statusNames().contains("wonder room")
            );
            assertEquals("1".equals(parts[9]), cleanup, scenario + " Wonder Room cleanup");
            assertEquals("1", parts[10], scenario + " unrelated status preservation oracle");
        }
    }

    @Test
    void progressionRejectsMismatchedFieldKindsBeforeProcessing() {
        FieldEffectEntry zone = new FieldEffectEntry(FieldEffectKind.ZONE, "Fog Zone", 2);
        try {
            FieldRoundProgression.advance(1, zone, List.of(), List.of());
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("terrain"));
            return;
        }
        throw new AssertionError("expected mismatched field kind to fail closed");
    }

    private static Optional<FieldEffectEntry> parseOne(FieldEffectKind kind, String encoded) {
        if (encoded == null || encoded.isBlank()) return Optional.empty();
        String[] parts = encoded.split("~", -1);
        Integer remaining = parts.length < 2 || parts[1].isBlank() ? null : Integer.valueOf(parts[1]);
        return Optional.of(new FieldEffectEntry(kind, parts[0], remaining));
    }

    private static List<FieldEffectEntry> parseMany(FieldEffectKind kind, String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        ArrayList<FieldEffectEntry> entries = new ArrayList<>();
        for (String part : encoded.split(";")) {
            parseOne(kind, part).ifPresent(entries::add);
        }
        return List.copyOf(entries);
    }

    private static String encodeOptional(Optional<FieldEffectEntry> entry) {
        return entry.map(FieldRoundProgressionOracleParityTest::encodeEntry).orElse("");
    }

    private static String encodeMany(List<FieldEffectEntry> entries) {
        return String.join(";", entries.stream().map(FieldRoundProgressionOracleParityTest::encodeEntry).toList());
    }

    private static String encodeEntry(FieldEffectEntry entry) {
        return entry.name() + "~" + (entry.remaining() == null ? "" : entry.remaining());
    }

    private static String encodeEvents(List<BattleEvent> events) {
        ArrayList<String> encoded = new ArrayList<>();
        for (BattleEvent event : events) {
            FieldEffectEndedEvent ended = (FieldEffectEndedEvent) event;
            encoded.add(String.join("|",
                    ended.fieldKind().wireName(), ended.effect(), ended.effectName(), Integer.toString(ended.round())));
        }
        return String.join(";", encoded);
    }
}
