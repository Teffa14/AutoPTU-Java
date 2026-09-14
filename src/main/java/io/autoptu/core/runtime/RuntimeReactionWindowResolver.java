package io.autoptu.core.runtime;

import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Discovers reaction windows from authoritative battle events without executing them.
 *
 * <p>The resolver composes trigger matching, content ownership, statuses, and per-round
 * usage while preserving deterministic battle insertion order. It does not commit usage,
 * consume action resources, choose targets, roll RNG, or execute the reaction.</p>
 */
public final class RuntimeReactionWindowResolver {
    private final BattleRuntimeState battleState;
    private final RuntimeReactionTriggerMatcher triggerMatcher;
    private final RuntimeReactionEligibilityResolver eligibilityResolver;

    public RuntimeReactionWindowResolver(BattleRuntimeState battleState) {
        this(
                battleState,
                RuntimeReactionTriggerMatcher.builtin(),
                new RuntimeReactionEligibilityResolver(
                        Objects.requireNonNull(battleState, "battleState"),
                        new RuntimeReactionUsageTracker(battleState)
                )
        );
    }

    RuntimeReactionWindowResolver(
            BattleRuntimeState battleState,
            RuntimeReactionTriggerMatcher triggerMatcher,
            RuntimeReactionEligibilityResolver eligibilityResolver
    ) {
        this.battleState = Objects.requireNonNull(battleState, "battleState");
        this.triggerMatcher = Objects.requireNonNull(triggerMatcher, "triggerMatcher");
        this.eligibilityResolver = Objects.requireNonNull(eligibilityResolver, "eligibilityResolver");
    }

    /**
     * Discovers eligible and unresolved reactors for one completed Shift.
     *
     * <p>Only trigger matches are evaluated for ownership/eligibility. UNKNOWN ownership is
     * retained separately so partial snapshots never become false denials.</p>
     */
    public Resolution discoverShiftWindows(
            String reactionKey,
            ShiftResolvedEvent event,
            ReactionEligibilityPolicy policy
    ) {
        if (reactionKey == null || reactionKey.isBlank()) {
            throw new IllegalArgumentException("reactionKey is required");
        }
        Objects.requireNonNull(event, "shift event");
        Objects.requireNonNull(policy, "policy");
        battleState.requireCombatant(event.actorId());

        ArrayList<Candidate> eligible = new ArrayList<>();
        ArrayList<UnresolvedCandidate> unresolved = new ArrayList<>();

        for (String reactorId : battleState.combatantIds()) {
            if (reactorId.equals(event.actorId())) {
                continue;
            }
            RuntimeReactionTriggerMatcher.TriggerMatch match = triggerMatcher
                    .matchShift(reactionKey, reactorId, battleState, event)
                    .orElse(null);
            if (match == null) {
                continue;
            }

            RuntimeReactionEligibilityResolver.Resolution eligibility = eligibilityResolver.evaluate(
                    reactorId,
                    reactionKey,
                    policy
            );
            if (!eligibility.ownershipKnown()) {
                unresolved.add(new UnresolvedCandidate(match, eligibility.ownership()));
                continue;
            }
            ReactionEligibilityPolicy.Eligibility decision = eligibility.eligibility().orElseThrow();
            if (decision.eligible()) {
                eligible.add(new Candidate(match, eligibility.ownership()));
            }
        }

        return new Resolution(List.copyOf(eligible), List.copyOf(unresolved));
    }

    public record Candidate(
            RuntimeReactionTriggerMatcher.TriggerMatch triggerMatch,
            RuntimeReactionOwnershipResolver.Result ownership
    ) {
        public Candidate {
            triggerMatch = Objects.requireNonNull(triggerMatch, "triggerMatch");
            ownership = Objects.requireNonNull(ownership, "ownership");
            if (!ownership.ownsReaction()) {
                throw new IllegalArgumentException("eligible candidate must own the reaction");
            }
        }

        public String reactorId() {
            return triggerMatch.reactorId();
        }

        public String triggeringActorId() {
            return triggerMatch.triggeringActorId();
        }
    }

    public record UnresolvedCandidate(
            RuntimeReactionTriggerMatcher.TriggerMatch triggerMatch,
            RuntimeReactionOwnershipResolver.Result ownership
    ) {
        public UnresolvedCandidate {
            triggerMatch = Objects.requireNonNull(triggerMatch, "triggerMatch");
            ownership = Objects.requireNonNull(ownership, "ownership");
            if (ownership.status() != RuntimeReactionOwnershipResolver.Status.UNKNOWN) {
                throw new IllegalArgumentException("unresolved candidate requires UNKNOWN ownership");
            }
        }

        public String reactorId() {
            return triggerMatch.reactorId();
        }
    }

    public record Resolution(List<Candidate> eligible, List<UnresolvedCandidate> unresolved) {
        public Resolution {
            eligible = eligible == null ? List.of() : List.copyOf(eligible);
            unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
        }
    }
}
