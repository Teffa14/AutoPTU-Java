package io.autoptu.core.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeReactionTriggerRegistryTest {
    @Test
    void builtinFreezesAttackOfOpportunityTriggerOrderAndManeuvers() {
        List<RuntimeReactionTriggerRegistry.TriggerDefinition> triggers =
                RuntimeReactionTriggerRegistry.builtin().resolve("Attack of Opportunity").orElseThrow();

        assertEquals(List.of(
                RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_NON_TARGETING_MANEUVER,
                RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_STAND_UP,
                RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET,
                RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_STANDARD_ITEM_RETRIEVAL,
                RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_SHIFT_AWAY
        ), triggers.stream().map(RuntimeReactionTriggerRegistry.TriggerDefinition::kind).toList());
        assertEquals(Set.of("push", "grapple", "disarm", "trip", "dirty_trick"), triggers.getFirst().qualifiers());
    }

    @Test
    void unknownReactionRemainsUnresolved() {
        assertTrue(RuntimeReactionTriggerRegistry.builtin().resolve("future_reaction").isEmpty());
    }

    @Test
    void rejectsDuplicateNormalizedReactionKeys() {
        Map<String, List<RuntimeReactionTriggerRegistry.TriggerDefinition>> definitions = new java.util.LinkedHashMap<>();
        List<RuntimeReactionTriggerRegistry.TriggerDefinition> triggers = List.of(
                new RuntimeReactionTriggerRegistry.TriggerDefinition(
                        RuntimeReactionTriggerRegistry.TriggerKind.ADJACENT_STAND_UP,
                        Set.of())
        );
        definitions.put("Attack of Opportunity", triggers);
        definitions.put("attack_of_opportunity", triggers);

        assertThrows(IllegalArgumentException.class, () -> new RuntimeReactionTriggerRegistry(definitions));
    }
}
