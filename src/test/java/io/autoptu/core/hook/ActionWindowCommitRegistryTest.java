package io.autoptu.core.hook;

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
}
