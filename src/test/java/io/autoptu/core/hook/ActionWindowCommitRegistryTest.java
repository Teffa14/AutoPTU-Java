package io.autoptu.core.hook;

import io.autoptu.core.model.ActionType;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.ActionSpendResult;
import io.autoptu.core.rules.ReactionResourceCommitter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionWindowCommitRegistryTest {

    private static ActionWindowCandidate candidate(String reactor, String action, String trigger) {
        return new ActionWindowCandidate(reactor, action, trigger);
    }

    @Test
    void commitsCandidateOnlyAfterCurrentStateValidation() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ActionWindowCandidate candidate = candidate("reactor", "attack_of_opportunity", "action:42");

        assertTrue(commits.commit(candidate, ignored -> true).isPresent());
        assertTrue(commits.isCommitted("action:42"));
        assertEquals(candidate, commits.committed("action:42").orElseThrow());
    }

    @Test
    void rejectsCandidateThatBecameStaleBeforeCommit() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ActionWindowCandidate candidate = candidate("reactor", "attack_of_opportunity", "action:42");

        assertTrue(commits.commit(candidate, ignored -> false).isEmpty());
        assertFalse(commits.isCommitted("action:42"));
        assertEquals(0, commits.committedCount());
    }

    @Test
    void triggerCanBeCommittedOnlyOnce() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ActionWindowCandidate first = candidate("reactor-a", "attack_of_opportunity", "action:42");
        ActionWindowCandidate competing = candidate("reactor-b", "attack_of_opportunity", "action:42");

        assertTrue(commits.commit(first, ignored -> true).isPresent());
        assertTrue(commits.commit(competing, ignored -> true).isEmpty());
        assertEquals(first, commits.committed("action:42").orElseThrow());
        assertEquals(1, commits.committedCount());
    }

    @Test
    void rejectedCandidateDoesNotConsumeTriggerClaim() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ActionWindowCandidate stale = candidate("reactor-a", "attack_of_opportunity", "action:42");
        ActionWindowCandidate live = candidate("reactor-b", "attack_of_opportunity", "action:42");

        assertTrue(commits.commit(stale, ignored -> false).isEmpty());
        assertTrue(commits.commit(live, ignored -> true).isPresent());
        assertEquals(live, commits.committed("action:42").orElseThrow());
    }

    @Test
    void failedResourceSpendDoesNotClaimTrigger() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ReactionResourceCommitter resources = new ReactionResourceCommitter();
        ActionBudget exhausted = new ActionBudget();
        exhausted.markAction(ActionType.STANDARD, "earlier action");
        ActionWindowCandidate first = candidate("reactor-a", "feature_reaction", "action:42");

        assertTrue(commits.commitWithResource(first, ignored -> true,
                new ActionWindowResourceCommit(ActionType.STANDARD, "feature reaction"),
                exhausted, resources).isEmpty());
        assertFalse(commits.isCommitted("action:42"));

        ActionBudget available = new ActionBudget();
        ActionWindowCandidate second = candidate("reactor-b", "feature_reaction", "action:42");
        assertTrue(commits.commitWithResource(second, ignored -> true,
                new ActionWindowResourceCommit(ActionType.STANDARD, "feature reaction"),
                available, resources).isPresent());
        assertFalse(available.hasActionAvailable(ActionType.STANDARD));
    }

    @Test
    void successfulResourceSpendClaimsTriggerBeforeCompetitorCanSpend() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ReactionResourceCommitter resources = new ReactionResourceCommitter();
        ActionBudget firstBudget = new ActionBudget();
        ActionBudget competingBudget = new ActionBudget();
        ActionWindowCandidate first = candidate("reactor-a", "feature_reaction", "action:42");
        ActionWindowCandidate competing = candidate("reactor-b", "feature_reaction", "action:42");
        ActionWindowResourceCommit cost = new ActionWindowResourceCommit(ActionType.SWIFT, "feature reaction");

        CommittedActionWindowInstruction instruction = commits.commitWithResource(
                first, ignored -> true, cost, firstBudget, resources).orElseThrow();
        assertEquals("reactor-a", instruction.reactingCombatantId());
        assertEquals("feature_reaction", instruction.actionKey());
        assertEquals("action:42", instruction.triggerKey());
        assertEquals(ActionType.SWIFT, instruction.actionType());
        assertEquals("feature reaction", instruction.resourceDetail());
        assertEquals(ActionSpendResult.Source.BASE, instruction.spend().source());
        assertFalse(firstBudget.hasActionAvailable(ActionType.SWIFT));
        assertTrue(commits.commitWithResource(competing, ignored -> true, cost, competingBudget, resources).isEmpty());
        assertTrue(competingBudget.hasActionAvailable(ActionType.SWIFT));
        assertEquals(first, commits.committed("action:42").orElseThrow());
    }

    @Test
    void instructionPreservesNamedExtraSpendProvenance() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ReactionResourceCommitter resources = new ReactionResourceCommitter();
        ActionBudget budget = new ActionBudget();
        budget.markAction(ActionType.STANDARD, "earlier action");
        budget.grantExtra(ActionType.STANDARD, "Commander grant", 1);
        ActionWindowCandidate candidate = candidate("reactor", "feature_reaction", "action:extra");

        CommittedActionWindowInstruction instruction = commits.commitWithResource(
                candidate,
                ignored -> true,
                new ActionWindowResourceCommit(ActionType.STANDARD, "feature reaction"),
                budget,
                resources
        ).orElseThrow();

        assertEquals(ActionSpendResult.Source.EXTRA, instruction.spend().source());
        assertEquals("Commander grant", instruction.spend().extraGrantName().orElseThrow());
    }

    @Test
    void freeReactionClaimsTriggerWithoutMutatingBudgetAndPreservesFreeProvenance() {
        ActionWindowCommitRegistry commits = new ActionWindowCommitRegistry();
        ReactionResourceCommitter resources = new ReactionResourceCommitter();
        ActionBudget budget = new ActionBudget();
        ActionWindowCandidate aoo = candidate("reactor", "attack_of_opportunity", "action:42");

        CommittedActionWindowInstruction instruction = commits.commitWithResource(
                aoo,
                ignored -> true,
                new ActionWindowResourceCommit(ActionType.FREE, "Attack of Opportunity"),
                budget,
                resources
        ).orElseThrow();
        assertEquals(ActionSpendResult.Source.FREE, instruction.spend().source());
        assertEquals(ActionType.FREE, instruction.actionType());
        assertTrue(budget.hasActionAvailable(ActionType.STANDARD));
        assertTrue(budget.hasActionAvailable(ActionType.SHIFT));
        assertTrue(budget.hasActionAvailable(ActionType.SWIFT));
    }
}
