package io.autoptu.core.hook;

import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionSpendResult;
import io.autoptu.core.rules.ReactionResourceCommitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative commit boundary for discovered action-window candidates.
 *
 * <p>Discovery is advisory. Commit revalidates the selected candidate against current core state
 * and claims its trigger key only after any required action-economy resource is paid. The actual
 * reaction instruction/execution remains a later resolver step.</p>
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

    public Optional<ActionWindowCandidate> commitWithResource(
            ActionWindowCandidate candidate,
            ActionWindowCommitValidator validator,
            ActionWindowResourceCommit resource,
            ActionBudget budget,
            ReactionResourceCommitter resourceCommitter
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(resourceCommitter, "resourceCommitter");

        if (committedByTrigger.containsKey(candidate.triggerKey())) {
            return Optional.empty();
        }
        if (!validator.canCommit(candidate)) {
            return Optional.empty();
        }

        ActionSpendResult spend = resourceCommitter.commit(budget, resource.actionType(), resource.detail());
        if (!spend.consumed()) {
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
