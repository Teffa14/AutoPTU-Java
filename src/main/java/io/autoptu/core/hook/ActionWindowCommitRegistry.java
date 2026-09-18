package io.autoptu.core.hook;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative commit boundary for discovered action-window candidates.
 *
 * <p>Discovery is advisory. Commit revalidates the selected candidate against current core state
 * and atomically claims its trigger key, preventing stale or duplicate reaction execution. The
 * actual reaction instruction/execution remains a later resolver step.</p>
 */
public final class ActionWindowCommitRegistry {
    private final Map<String, ActionWindowCandidate> committedByTrigger = new LinkedHashMap<>();

    public Optional<ActionWindowCandidate> commit(
            ActionWindowCandidate candidate,
            ActionWindowCommitValidator validator
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(validator, "validator");

        if (committedByTrigger.containsKey(candidate.triggerKey())) {
            return Optional.empty();
        }
        if (!validator.canCommit(candidate)) {
            return Optional.empty();
        }

        committedByTrigger.put(candidate.triggerKey(), candidate);
        return Optional.of(candidate);
    }

    public boolean isCommitted(String triggerKey) {
        String normalized = normalizeTriggerKey(triggerKey);
        return committedByTrigger.containsKey(normalized);
    }

    public Optional<ActionWindowCandidate> committed(String triggerKey) {
        return Optional.ofNullable(committedByTrigger.get(normalizeTriggerKey(triggerKey)));
    }

    public int committedCount() {
        return committedByTrigger.size();
    }

    private static String normalizeTriggerKey(String triggerKey) {
        if (triggerKey == null || triggerKey.isBlank()) {
            throw new IllegalArgumentException("trigger key is required");
        }
        return triggerKey.strip();
    }
}
