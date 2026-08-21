package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.FieldEffectEndedEvent;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MovementGrid;
import io.autoptu.core.model.MovementProfile;
import io.autoptu.core.rules.ActionBudget;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldRoundLifecycleOracleParityTest {
    @Test
    void roundStartMutatesCanonicalFieldStateAndMatchesPythonOracle() throws IOException {
        Path fixture = Path.of("build/oracle/field-round-progression.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        List<String> lines = Files.readAllLines(fixture);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            String[] parts = lines.get(i).split("\\t", -1);
            String scenario = parts[0];
            int round = Integer.parseInt(parts[1]);

            BattleRuntimeState state = state();
            state.syncEnvironmentFromRuntime(state.environment().withFieldEffects(
                    parseOne(FieldEffectKind.TERRAIN, parts[2]).orElse(null),
                    parseMany(FieldEffectKind.ZONE, parts[3]),
                    parseMany(FieldEffectKind.ROOM, parts[4])
            ));
            BattleRoundController controller = new BattleRoundController(state, round - 1);

            RoundStartResult result = controller.startRoundWithEvents();

            assertEquals(round, result.round(), scenario + " round");
            assertEquals(parts[5], encodeOptional(state.environment().terrainEffect()), scenario + " terrain");
            assertEquals(parts[6], encodeMany(state.environment().zoneEffects()), scenario + " zones");
            assertEquals(parts[7], encodeMany(state.environment().roomEffects()), scenario + " rooms");
            assertEquals(parts[8], encodeFieldEvents(result.events()), scenario + " events");

            if ("1".equals(parts[9])) {
                assertFalse(state.hasStatus("alpha", "wondered"), scenario + " alpha Wondered cleanup");
                assertFalse(state.hasStatus("beta", "wondered"), scenario + " beta Wondered cleanup");
            } else {
                assertTrue(state.hasStatus("alpha", "wondered"), scenario + " alpha Wondered preserved");
                assertTrue(state.hasStatus("beta", "wondered"), scenario + " beta Wondered preserved");
            }
            assertEquals("1".equals(parts[10]), state.hasStatus("alpha", "burned"), scenario + " Burn preserved");
            assertEquals("1".equals(parts[10]), state.hasStatus("beta", "burned"), scenario + " Burn preserved beta");
        }
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState alpha = combatant("alpha", new GridCoord(0, 0));
        RuntimeCombatantState beta = combatant("beta", new GridCoord(2, 0));
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(alpha, beta),
                Map.of(
                        "alpha", Set.of("wondered", "burned"),
                        "beta", Set.of("wondered", "burned")
                )
        );
    }

    private static RuntimeCombatantState combatant(String id, GridCoord position) {
        return new RuntimeCombatantState(
                id,
                MovementProfile.walking(position, 1),
                20,
                20,
                new ActionBudget()
        );
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
        for (String part : encoded.split(";")) parseOne(kind, part).ifPresent(entries::add);
        return List.copyOf(entries);
    }

    private static String encodeOptional(Optional<FieldEffectEntry> entry) {
        return entry.map(FieldRoundLifecycleOracleParityTest::encodeEntry).orElse("");
    }

    private static String encodeMany(List<FieldEffectEntry> entries) {
        return String.join(";", entries.stream().map(FieldRoundLifecycleOracleParityTest::encodeEntry).toList());
    }

    private static String encodeEntry(FieldEffectEntry entry) {
        return entry.name() + "~" + (entry.remaining() == null ? "" : entry.remaining());
    }

    private static String encodeFieldEvents(List<BattleEvent> events) {
        ArrayList<String> encoded = new ArrayList<>();
        for (BattleEvent event : events) {
            if (!(event instanceof FieldEffectEndedEvent ended)) continue;
            encoded.add(String.join("|",
                    ended.fieldKind().wireName(), ended.effect(), ended.effectName(), Integer.toString(ended.round())));
        }
        return String.join(";", encoded);
    }
}
