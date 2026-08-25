package io.autoptu.core.hook;

import io.autoptu.core.runtime.TemporaryEffectStore;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoveSpecialEffectRollTemporaryStateOracleParityTest {
    @Test
    void temporaryEffectExpiryAndShortCircuitingMatchPinnedPython() throws IOException {
        Path fixture = Path.of(System.getProperty(
                "autoptu.move.special.effect.roll.state.oracle",
                "build/oracle/move-special-effect-roll-state.tsv"));
        Assumptions.assumeTrue(Files.exists(fixture));
        Map<String, Expected> expected = new LinkedHashMap<>();
        for (String line : Files.readAllLines(fixture)) {
            if (line == null || line.isBlank()) continue;
            String[] p = line.split("\\t", -1);
            expected.put(p[0], new Expected(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
        }

        assertScenario(expected.get("expired_immutable_then_clear"), effects(), effects(entry("immutable_mind_block", "move", "Test", "expires_round", 2)));
        assertScenario(expected.get("immutable_other_move_survives"), effects(), effects(entry("immutable_mind_block", "move", "Other", "expires_round", 5)));
        assertScenario(expected.get("expired_range_block_then_live"), effects(
                entry("effect_range_block", "expires_round", 2),
                entry("effect_range_block", "expires_round", 5),
                entry("effect_range_bonus", "amount", 9, "expires_round", 2)), effects());
        assertScenario(expected.get("expired_and_live_bonus"), effects(
                entry("effect_range_bonus", "amount", 4, "expires_round", 2),
                entry("effect_range_bonus", "amount", "3", "expires_round", 5),
                entry("effect_range_bonus", "amount", "bad", "expires_round", 5)), effects());
        assertScenario(expected.get("immutable_short_circuits_attacker_cleanup"), effects(
                entry("effect_range_bonus", "amount", 4, "expires_round", 2)), effects(
                entry("immutable_mind_block", "move", "Test", "expires_round", 5)));
    }

    private static void assertScenario(Expected expected, TemporaryEffectStore attacker, TemporaryEffectStore defender) {
        MoveSpecialEffectRollTemporaryStateResolution.Result state =
                MoveSpecialEffectRollTemporaryStateResolution.resolve(attacker, defender, "Test", 3);
        int roll = switch (state.block()) {
            case IMMUTABLE_MIND -> -1;
            case EFFECT_RANGE -> 0;
            case NONE -> 10 + state.effectRangeBonuses().stream().mapToInt(Integer::intValue).sum();
        };
        assertEquals(expected.roll(), roll);
        assertEquals(expected.rangeBlocks(), attacker.count("effect_range_block"));
        assertEquals(expected.rangeBonuses(), attacker.count("effect_range_bonus"));
        assertEquals(expected.immutableBlocks(), defender.count("immutable_mind_block"));
    }

    private static TemporaryEffectStore effects(Map<String, Object>... entries) {
        TemporaryEffectStore store = new TemporaryEffectStore();
        for (Map<String, Object> entry : entries) {
            String kind = String.valueOf(entry.get("kind"));
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>(entry);
            payload.remove("kind");
            store.add(kind, payload);
        }
        return store;
    }

    private static Map<String, Object> entry(String kind, Object... pairs) {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("kind", kind);
        for (int i = 0; i < pairs.length; i += 2) out.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return out;
    }

    private record Expected(int roll, int rangeBlocks, int rangeBonuses, int immutableBlocks) {}
}
