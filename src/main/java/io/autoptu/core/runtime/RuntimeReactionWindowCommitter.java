package io.autoptu.core.runtime;

import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.Objects;

/**
 * Commits one already-discovered reaction window against current authoritative battle state.
 *
 * <p>Commit revalidates ownership, statuses, round identity, and per-round usage immediately before
 * recording the use. It deliberately does not choose a reaction, consume ordinary action buckets,
 * roll RNG, select targets, or execute the reaction. Those remain later runtime transitions.</p>
 */
public final class RuntimeReactionWindowCommitter {
    private final BattleRuntimeState battleState;
    private final RuntimeReactionUsageTracker usageTracker;
    private final RuntimeReactionEligibilityResolver eligibilityResolver;

    public RuntimeReactionWindowCommitter(BattleRuntimeState battleState) {
        this(
                Objects.requireNonNull(battleState, "battleState"),
                new RuntimeReactionUsageTracker(battleState)
        );
    }

    RuntimeReactionWindowCommitter(
            BattleRuntimeState battleState,
            RuntimeReactionUsageTracker usageTracker
    ) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
        this.usageTracker = Objects.requireNonNull(usageTracker, "usageTracker");
        this.eligibilityResolver = new RuntimeReactionEligibilityResolver(battleState, usageTracker);
    }

    /**
     * Attempts to commit a reaction opportunity exactly once against current battle state.
     */
    public CommitResult commit(
            RuntimeReactionWindowResolver.Candidate candidate,
            ReactionEligibilityPolicy policy
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(policy, "policy");

        RuntimeReactionWindow window = candidate.window();
        if (window.round() != battleState.currentRound()) {
            return new CommitResult(false, CommitReason.STALE_ROUND);
        }
        if (!window.reactorId().equals(candidate.reactorId())) {
            throw new IllegalArgumentException("candidate reactor does not match reaction window");
        }

        RuntimeReactionEligibilityResolver.Resolution current = eligibilityResolver.evaluate(
                window.reactorId(),
                window.reactionKey(),
                policy
        );
        if (!current.ownershipKnown()) {
            return new CommitResult(false, CommitReason.OWNERSHIP_UNRESOLVED);
        }
        ReactionEligibilityPolicy.Eligibility eligibility = current.eligibility().orElseThrow();
        if (!eligibility.eligible()) {
            return new CommitResult(false, switch (eligibility.reason()) {
                case MISSING_OWNERSHIP -> CommitReason.MISSING_OWNERSHIP;
                case BLOCKED_BY_STATUS -> CommitReason.BLOCKED_BY_STATUS;
                case ROUND_USE_EXHAUSTED -> CommitReason.ROUND_USE_EXHAUSTED;
                case ELIGIBLE -> throw new IllegalStateException("eligible decision cannot be rejected");
            });
        }

        usageTracker.recordUseFromRuntime(window.reactorId(), window.reactionKey());
        return new CommitResult(true, CommitReason.COMMITTED);
    }

    public record CommitResult(boolean committed, CommitReason reason) {
        public CommitResult {
            reason = Objects.requireNonNull(reason, "reason");
            if (committed != (reason == CommitReason.COMMITTED)) {
                throw new IllegalArgumentException("committed must match COMMITTED reason");
            }
        }
    }

    public enum CommitReason {
        COMMITTED,
        STALE_ROUND,
        OWNERSHIP_UNRESOLVED,
        MISSING_OWNERSHIP,
        BLOCKED_BY_STATUS,
        ROUND_USE_EXHAUSTED
    }
}
