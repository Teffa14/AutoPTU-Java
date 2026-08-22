package io.autoptu.core.runtime;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundTemporaryEffectExpiryOracleParityTest {
    @Test
    void roundStartFollowMeAndForesightExpiryMatchesPythonOracle() throws IOException {
        Path fixture = Path.of("build/oracle/round-temporary-expiry.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        List<String> lines = Files.readAllLines(fixture);
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isBlank()) continue;
            String[] parts = line.split("\\t", -1);
            String scenario = parts[0];
            int round = Integer.parseInt(parts[1]);

            BattleRuntimeState state = state();
            TemporaryEffectStore store = state.requireCombatant("alpha").temporaryEffects();
            for (EncodedEffect effect : parse(parts[2])) {
                store.add(effect.name(), effect.payload());
            }

            RoundStartResult result = new BattleRoundController(state, round - 1).startRoundWithEvents();

            assertEquals(round, result.round(), scenario + " round");
            assertEquals(parts[3], encode(store.entriesInInsertionOrder()), scenario + " temporary effects");
        }
    }

    @Test
    void genericExpiryCanBeReusedWithoutLifecycleController() {
        BattleRuntimeState state = state();
        TemporaryEffectStore store = state.requireCombatant("alpha").temporaryEffects();
        store.add("follow_me", Map.of("until_round", 1, "tag", "old"));
        store.add("foresight", Map.of("until_round", 9, "tag", "future"));

        assertEquals(1, RoundTemporaryEffectExpiry.expireFamily(state, 2, "follow_me"));
        assertEquals(0, RoundTemporaryEffectExpiry.expireFamily(state, 2, "foresight"));
        assertEquals("foresight~9~future", encode(store.entriesInInsertionOrder()));
    }

    private static BattleRuntimeState state() {
        RuntimeCombatantState alpha = new RuntimeCombatantState(
                "alpha",
                MovementProfile.walking(new GridCoord(0, 0), 1),
                20,
                20,
                new ActionBudget()
        );
        return new BattleRuntimeState(
                new MovementGrid(4, 4, Set.of(), Map.of()),
                List.of(alpha)
        );
    }

    private static List<EncodedEffect> parse(String encoded) {
        if (encoded == null || encoded.isBlank()) return List.of();
        ArrayList<EncodedEffect> effects = new ArrayList<>();
        for (String raw : encoded.split(";", -1)) {
            String[] parts = raw.split("~", -1);
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            if (parts.length > 1 && !parts[1].isEmpty()) payload.put("until_round", parts[1]);
            if (parts.length > 2 && !parts[2].isEmpty()) payload.put("tag", parts[2]);
            effects.add(new EncodedEffect(parts[0], payload));
        }
        return List.copyOf(effects);
    }

    private static String encode(List<TemporaryEffectEntry> entries) {
        ArrayList<String> encoded = new ArrayList<>();
        for (TemporaryEffectEntry entry : entries) {
            Object until = entry.payload().get("until_round");
            Object tag = entry.payload().get("tag");
            encoded.add(entry.name()
                    + "~" + (until == null ? "" : until)
                    + "~" + (tag == null ? "" : tag));
        }
        return String.join(";", encoded);
    }

    private record EncodedEffect(String name, Map<String, Object> payload) {}
}
