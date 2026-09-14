package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionTimingRegistryTest {
    @Test
    void builtinMapsAttackOfOpportunityToInterrupt() {
        RuntimeReactionTimingRegistry registry = RuntimeReactionTimingRegistry.builtin();

        assertEquals(
                RuntimeReactionTimingRegistry.Timing.INTERRUPT,
                registry.resolve("attack_of_opportunity").orElseThrow()
        );
        assertEquals(
                RuntimeReactionTimingRegistry.Timing.INTERRUPT,
                registry.resolve("Attack of Opportunity").orElseThrow()
        );
    }

    @Test
    void unknownReactionRemainsUnresolved() {
        assertTrue(RuntimeReactionTimingRegistry.builtin().resolve("future_reaction").isEmpty());
    }

    @Test
    void rejectsDuplicateKeysAfterNormalization() {
        Map<String, RuntimeReactionTimingRegistry.Timing> definitions = new java.util.LinkedHashMap<>();
        definitions.put("Attack of Opportunity", RuntimeReactionTimingRegistry.Timing.INTERRUPT);
        definitions.put("attack_of_opportunity", RuntimeReactionTimingRegistry.Timing.INTERRUPT);

        assertThrows(IllegalArgumentException.class, () -> new RuntimeReactionTimingRegistry(definitions));
    }
}
