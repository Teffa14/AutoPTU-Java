package io.autoptu.core.runtime;

import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.Objects;

/**
 * Runtime bridge from authoritative battle state to a pure reaction eligibility policy.
 *
 * <p>Status names and the current round come from server-owned state. Content systems still
 * provide ownership and the declarative policy; this resolver does not spend Interrupt,
 * commit usage, choose a target, consume RNG, or execute the reaction.</p>
 */
public final class RuntimeReactionEligibilityResolver {
    private final BattleRuntimeState battleState;
    private final RuntimeReactionUsageTracker usageTracker;

    public RuntimeReactionEligibilityResolver(
            BattleRuntimeState battleState,
            RuntimeReactionUsageTracker usageTracker
    ) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
        this.usageTracker = Objects.requireNonNull(usageTracker, "usageTracker");
    }

    public ReactionEligibilityPolicy.Eligibility evaluate(
            String combatantId,
            String reactionKey,
            boolean ownsReaction,
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
        return policy.evaluate(new ReactionEligibilityPolicy.Context(
                ownsReaction,
                battleState.statuses(canonicalCombatantId),
                usageTracker.usesThisRound(canonicalCombatantId, reactionKey)
        ));
    }
}
