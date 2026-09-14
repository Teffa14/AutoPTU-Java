package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Declarative registry for reaction execution timing.
 *
 * <p>Timing is intentionally separate from action-budget cost. The pinned Python
 * oracle identifies Attack of Opportunity as an Interrupt, but does not establish
 * a generic consumable Interrupt bucket in the ordinary action economy.</p>
 */
public final class RuntimeReactionTimingRegistry {
    private final Map<String, Timing> timingByReactionKey;

    public RuntimeReactionTimingRegistry(Map<String, Timing> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("at least one reaction timing definition is required");
        }
        LinkedHashMap<String, Timing> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Timing> entry : definitions.entrySet()) {
            String key = MoveReactionOwnershipSource.normalizeKey(entry.getKey());
            Timing previous = normalized.put(key, Objects.requireNonNull(entry.getValue(), "reaction timing"));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate normalized reaction timing key: " + key);
            }
        }
        this.timingByReactionKey = Map.copyOf(normalized);
    }

    public static RuntimeReactionTimingRegistry builtin() {
        return new RuntimeReactionTimingRegistry(Map.of(
                "attack_of_opportunity", Timing.INTERRUPT
        ));
    }

    public Optional<Timing> resolve(String reactionKey) {
        return Optional.ofNullable(timingByReactionKey.get(MoveReactionOwnershipSource.normalizeKey(reactionKey)));
    }

    public Map<String, Timing> definitions() {
        return timingByReactionKey;
    }

    public enum Timing {
        INTERRUPT
    }
}
