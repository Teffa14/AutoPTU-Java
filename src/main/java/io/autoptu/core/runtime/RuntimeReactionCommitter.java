package io.autoptu.core.runtime;

import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Atomic runtime boundary for committing an already-triggered reaction.
 *
 * <p>This service derives ownership, statuses, and per-round usage from authoritative battle state.
 * If eligibility is known and allowed, it records exactly one use in the canonical reaction ledger.
 * It deliberately does not spend Interrupt/Priority resources, choose a target, consume RNG, or execute
 * the reaction body; those remain separate contracts until their Python/rule-profile semantics are frozen.</p>
 */
public final class RuntimeReactionCommitter {
    private final RuntimeReactionUsageTracker usageTracker;
    private final RuntimeReactionEligibilityResolver eligibilityResolver;

    public RuntimeReactionCommitter(BattleRuntimeState battleState) {
        Objects.requireNonNull(battleState, "battleState");
        this.usageTracker = new RuntimeReactionUsageTracker(battleState);
        this.eligibilityResolver = new RuntimeReactionEligibilityResolver(battleState, usageTracker);
    }

    public Result commit(
            String combatantId,
            String reactionKey,
            ReactionEligibilityPolicy policy
    ) {
        RuntimeReactionEligibilityResolver.Resolution resolution = eligibilityResolver.evaluate(
                combatantId,
                reactionKey,
                policy
        );
        if (!resolution.ownershipKnown()) {
            return new Result(Status.OWNERSHIP_UNKNOWN, resolution, usageTracker.usesThisRound(combatantId, reactionKey));
        }

        ReactionEligibilityPolicy.Eligibility eligibility = resolution.eligibility().orElseThrow();
        if (!eligibility.eligible()) {
            return new Result(Status.BLOCKED, resolution, usageTracker.usesThisRound(combatantId, reactionKey));
        }

        usageTracker.recordUseFromRuntime(combatantId, reactionKey);
        return new Result(Status.COMMITTED, resolution, usageTracker.usesThisRound(combatantId, reactionKey));
    }

    public int usesThisRound(String combatantId, String reactionKey) {
        return usageTracker.usesThisRound(combatantId, reactionKey);
    }

    public enum Status {
        COMMITTED,
        BLOCKED,
        OWNERSHIP_UNKNOWN
    }

    public record Result(
            Status status,
            RuntimeReactionEligibilityResolver.Resolution eligibilityResolution,
            int usesThisRound
    ) {
        public Result {
            status = Objects.requireNonNull(status, "status");
            eligibilityResolution = Objects.requireNonNull(eligibilityResolution, "eligibilityResolution");
            if (usesThisRound < 0) {
                throw new IllegalArgumentException("usesThisRound cannot be negative");
            }
        }

        public boolean committed() {
            return status == Status.COMMITTED;
        }

        public Optional<ReactionEligibilityPolicy.Eligibility> eligibility() {
            return eligibilityResolution.eligibility();
        }
    }
}
