package io.autoptu.core.runtime;

import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.Objects;
import java.util.Optional;

/**
 * Runtime bridge from authoritative battle state to a pure reaction eligibility policy.
 *
 * <p>Status names, reaction ownership, and per-round usage come from server-owned state.
 * UNKNOWN ownership is preserved instead of being converted into a denial. This resolver
 * does not spend Interrupt, commit usage, choose a target, consume RNG, or execute the reaction.</p>
 */
public final class RuntimeReactionEligibilityResolver {
    private final BattleRuntimeState battleState;
    private final RuntimeReactionUsageTracker usageTracker;
    private final RuntimeReactionOwnershipResolver ownershipResolver;

    public RuntimeReactionEligibilityResolver(
            BattleRuntimeState battleState,
            RuntimeReactionUsageTracker usageTracker
    ) {
        this(battleState, usageTracker, new RuntimeReactionOwnershipResolver());
    }

    public RuntimeReactionEligibilityResolver(
            BattleRuntimeState battleState,
            RuntimeReactionUsageTracker usageTracker,
            RuntimeReactionOwnershipResolver ownershipResolver
    ) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
        this.usageTracker = Objects.requireNonNull(usageTracker, "usageTracker");
        this.ownershipResolver = Objects.requireNonNull(ownershipResolver, "ownershipResolver");
    }

    public Resolution evaluate(
            String combatantId,
            String reactionKey,
            ReactionEligibilityPolicy policy
    ) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        if (reactionKey == null || reactionKey.isBlank()) {
            throw new IllegalArgumentException("reactionKey is required");
        }
        Objects.requireNonNull(policy, "policy");

        String canonicalCombatantId = combatantId.strip();
        battleState.requireCombatant(canonicalCombatantId);
        RuntimeReactionOwnershipResolver.Result ownership = ownershipResolver.resolve(
                battleState,
                canonicalCombatantId,
                reactionKey
        );
        if (ownership.status() == RuntimeReactionOwnershipResolver.Status.UNKNOWN) {
            return new Resolution(ownership, Optional.empty());
        }

        ReactionEligibilityPolicy.Eligibility eligibility = policy.evaluate(new ReactionEligibilityPolicy.Context(
                ownership.status() == RuntimeReactionOwnershipResolver.Status.OWNED,
                battleState.statuses(canonicalCombatantId),
                usageTracker.usesThisRound(canonicalCombatantId, reactionKey)
        ));
        return new Resolution(ownership, Optional.of(eligibility));
    }

    public record Resolution(
            RuntimeReactionOwnershipResolver.Result ownership,
            Optional<ReactionEligibilityPolicy.Eligibility> eligibility
    ) {
        public Resolution {
            ownership = Objects.requireNonNull(ownership, "ownership");
            eligibility = eligibility == null ? Optional.empty() : eligibility;
            boolean ownershipUnknown = ownership.status() == RuntimeReactionOwnershipResolver.Status.UNKNOWN;
            if (ownershipUnknown == eligibility.isPresent()) {
                throw new IllegalArgumentException("eligibility must be absent exactly when ownership is UNKNOWN");
            }
        }

        public boolean ownershipKnown() {
            return ownership.status() != RuntimeReactionOwnershipResolver.Status.UNKNOWN;
        }
    }
}
