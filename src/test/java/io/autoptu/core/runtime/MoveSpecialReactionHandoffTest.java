package io.autoptu.core.runtime;

import io.autoptu.core.hook.PreDamageReactionResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveSpecialReactionHandoffTest {
    @Test
    void reactionUpdatesOwnedResultFieldsAndPreservesMoveSpecialState() {
        LinkedHashMap<String, Object> shared = new LinkedHashMap<>();
        shared.put("hit", true);
        shared.put("crit", true);
        shared.put("damage", 12);
        shared.put("type_multiplier", 2.0d);
        shared.put("marker", "from-pre-special");

        Map<String, Object> result = MoveSpecialReactionHandoff.apply(
                shared,
                PreDamageReactionResult.of(false, 0, 0.0d)
        );

        assertEquals(false, result.get("hit"));
        assertEquals(true, result.get("crit"));
        assertEquals(0, ((Number) result.get("damage")).intValue());
        assertEquals(0.0d, ((Number) result.get("type_multiplier")).doubleValue());
        assertEquals("from-pre-special", result.get("marker"));
        assertThrows(UnsupportedOperationException.class, () -> result.put("damage", 999));
    }

    @Test
    void successfulReactionCarriesAdjustedDamageIntoLaterMoveSpecialPhase() {
        Map<String, Object> result = MoveSpecialReactionHandoff.apply(
                Map.of("hit", true, "crit", false, "damage", 9, "type_multiplier", 1.0d),
                PreDamageReactionResult.of(true, 4, 0.5d)
        );

        assertEquals(true, result.get("hit"));
        assertEquals(false, result.get("crit"));
        assertEquals(4, ((Number) result.get("damage")).intValue());
        assertEquals(0.5d, ((Number) result.get("type_multiplier")).doubleValue());
        assertTrue(result.containsKey("crit"));
    }
}
