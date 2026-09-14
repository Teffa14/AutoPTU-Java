package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReactionEligibilityPolicyTest {
    private final ReactionEligibilityPolicy policy = ReactionEligibilityPolicy.attackOfOpportunity();

    @Test
    void acceptsOwnedUnusedReactionWithoutBlockingStatus() {
        assertReason(true, ReactionEligibilityPolicy.Reason.ELIGIBLE, true, Set.of(), 0);
    }

    @Test
    void requiresOwnership() {
        assertReason(false, ReactionEligibilityPolicy.Reason.MISSING_OWNERSHIP, false, Set.of(), 0);
    }

    @Test
    void blocksEachPinnedOracleStatusCaseInsensitively() {
        for (String status : new String[]{"Sleeping", "FLINCHED", "paralyzed"}) {
            assertReason(false, ReactionEligibilityPolicy.Reason.BLOCKED_BY_STATUS, true, Set.of(status), 0);
        }
    }

    @Test
    void unrelatedStatusDoesNotBlock() {
        assertReason(true, ReactionEligibilityPolicy.Reason.ELIGIBLE, true, Set.of("Burned"), 0);
    }

    @Test
    void enforcesOncePerRoundWithoutOwningTheUsageLedger() {
        assertReason(false, ReactionEligibilityPolicy.Reason.ROUND_USE_EXHAUSTED, true, Set.of(), 1);
        assertReason(false, ReactionEligibilityPolicy.Reason.ROUND_USE_EXHAUSTED, true, Set.of(), 2);
        assertReason(true, ReactionEligibilityPolicy.Reason.ELIGIBLE, true, Set.of(), 0);
    }

    private void assertReason(boolean eligible, ReactionEligibilityPolicy.Reason reason, boolean owns, Set<String> statuses, int uses) {
        ReactionEligibilityPolicy.Eligibility result = policy.evaluate(new ReactionEligibilityPolicy.Context(owns, statuses, uses));
        assertEquals(eligible, result.eligible());
        assertEquals(reason, result.reason());
    }
}
