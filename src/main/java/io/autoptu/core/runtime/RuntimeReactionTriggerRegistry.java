package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Declarative trigger windows for reactions, independent from eligibility and execution. */
public final class RuntimeReactionTriggerRegistry {
    private final Map<String, List<TriggerDefinition>> triggersByReactionKey;

    public RuntimeReactionTriggerRegistry(Map<String, List<TriggerDefinition>> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("at least one reaction trigger definition is required");
        }
        LinkedHashMap<String, List<TriggerDefinition>> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, List<TriggerDefinition>> entry : definitions.entrySet()) {
            String key = MoveReactionOwnershipSource.normalizeKey(entry.getKey());
            List<TriggerDefinition> triggers = List.copyOf(Objects.requireNonNull(entry.getValue(), "reaction triggers"));
            if (triggers.isEmpty()) {
                throw new IllegalArgumentException("reaction trigger list must not be empty: " + key);
            }
            if (normalized.put(key, triggers) != null) {
                throw new IllegalArgumentException("duplicate normalized reaction trigger key: " + key);
            }
        }
        this.triggersByReactionKey = Map.copyOf(normalized);
    }

    public static RuntimeReactionTriggerRegistry builtin() {
        return new RuntimeReactionTriggerRegistry(Map.of(
                "attack_of_opportunity", List.of(
                        new TriggerDefinition(TriggerKind.ADJACENT_NON_TARGETING_MANEUVER,
                                Set.of("push", "grapple", "disarm", "trip", "dirty_trick")),
                        new TriggerDefinition(TriggerKind.ADJACENT_STAND_UP, Set.of()),
                        new TriggerDefinition(TriggerKind.ADJACENT_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET, Set.of()),
                        new TriggerDefinition(TriggerKind.ADJACENT_STANDARD_ITEM_RETRIEVAL, Set.of()),
                        new TriggerDefinition(TriggerKind.ADJACENT_SHIFT_AWAY, Set.of())
                )
        ));
    }

    public Optional<List<TriggerDefinition>> resolve(String reactionKey) {
        return Optional.ofNullable(triggersByReactionKey.get(MoveReactionOwnershipSource.normalizeKey(reactionKey)));
    }

    public Map<String, List<TriggerDefinition>> definitions() {
        return triggersByReactionKey;
    }

    public record TriggerDefinition(TriggerKind kind, Set<String> qualifiers) {
        public TriggerDefinition {
            kind = Objects.requireNonNull(kind, "kind");
            qualifiers = Set.copyOf(Objects.requireNonNull(qualifiers, "qualifiers"));
        }
    }

    public enum TriggerKind {
        ADJACENT_NON_TARGETING_MANEUVER,
        ADJACENT_STAND_UP,
        ADJACENT_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET,
        ADJACENT_STANDARD_ITEM_RETRIEVAL,
        ADJACENT_SHIFT_AWAY
    }
}
