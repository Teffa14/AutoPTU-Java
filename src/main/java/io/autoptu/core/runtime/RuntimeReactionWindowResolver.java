package io.autoptu.core.runtime;

import io.autoptu.core.event.ActionResolvedEvent;
import io.autoptu.core.event.BattleEventOccurrence;
import io.autoptu.core.event.ShiftResolvedEvent;
import io.autoptu.core.hook.ReactionEligibilityPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Discovers reaction windows from authoritative battle events without executing them. */
public final class RuntimeReactionWindowResolver {
    private final BattleRuntimeState battleState;
    private final RuntimeReactionTriggerMatcher triggerMatcher;
    private final RuntimeReactionEligibilityResolver eligibilityResolver;

    public RuntimeReactionWindowResolver(BattleRuntimeState battleState) {
        this(battleState, RuntimeReactionTriggerMatcher.builtin(), new RuntimeReactionEligibilityResolver(
                Objects.requireNonNull(battleState, "battleState"), new RuntimeReactionUsageTracker(battleState)));
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

    public Resolution discoverShiftWindows(
            String reactionKey, ShiftResolvedEvent event, ReactionEligibilityPolicy policy
    ) {
        return discoverShiftWindowsInternal(reactionKey, event, null, policy);
    }

    public Resolution discoverShiftWindows(
            String reactionKey, BattleEventOccurrence occurrence, ReactionEligibilityPolicy policy
    ) {
        Objects.requireNonNull(occurrence, "event occurrence");
        if (!(occurrence.event() instanceof ShiftResolvedEvent event)) {
            throw new IllegalArgumentException("Shift reaction discovery requires a ShiftResolvedEvent occurrence");
        }
        return discoverShiftWindowsInternal(reactionKey, event, occurrence, policy);
    }

    /** Discovers registry-classified adjacent-action reactions from one authoritative occurrence. */
    public Resolution discoverAdjacentActionWindows(
            String reactionKey, BattleEventOccurrence occurrence, ReactionEligibilityPolicy policy
    ) {
        validateInputs(reactionKey, policy);
        Objects.requireNonNull(occurrence, "event occurrence");
        if (!(occurrence.event() instanceof ActionResolvedEvent event)) {
            throw new IllegalArgumentException("Adjacent action discovery requires an ActionResolvedEvent occurrence");
        }
        battleState.requireCombatant(event.actorId());
        return discover(
                reactionKey,
                event.actorId(),
                occurrence,
                policy,
                reactorId -> triggerMatcher.matchAdjacentOccurrence(reactionKey, reactorId, battleState, event).orElse(null)
        );
    }

    private Resolution discoverShiftWindowsInternal(
            String reactionKey,
            ShiftResolvedEvent event,
            BattleEventOccurrence occurrence,
            ReactionEligibilityPolicy policy
    ) {
        validateInputs(reactionKey, policy);
        Objects.requireNonNull(event, "shift event");
        battleState.requireCombatant(event.actorId());
        return discover(
                reactionKey,
                event.actorId(),
                occurrence,
                policy,
                reactorId -> triggerMatcher.matchShift(reactionKey, reactorId, battleState, event).orElse(null)
        );
    }

    private Resolution discover(
            String reactionKey,
            String actorId,
            BattleEventOccurrence occurrence,
            ReactionEligibilityPolicy policy,
            MatchResolver matchResolver
    ) {
        ArrayList<Candidate> eligible = new ArrayList<>();
        ArrayList<UnresolvedCandidate> unresolved = new ArrayList<>();
        for (String reactorId : battleState.combatantIds()) {
            if (reactorId.equals(actorId)) continue;
            RuntimeReactionTriggerMatcher.TriggerMatch match = matchResolver.match(reactorId);
            if (match == null) continue;
            RuntimeReactionWindow window;
            if (occurrence != null) {
                window = RuntimeReactionWindow.from(reactionKey, battleState.currentRound(), match, occurrence);
            } else {
                throw new IllegalStateException("payload-only discovery must construct its window before generic discovery");
            }
            RuntimeReactionEligibilityResolver.Resolution eligibility = eligibilityResolver.evaluate(
                    reactorId, reactionKey, policy);
            if (!eligibility.ownershipKnown()) {
                unresolved.add(new UnresolvedCandidate(window, match, eligibility.ownership()));
                continue;
            }
            if (eligibility.eligibility().orElseThrow().eligible()) {
                eligible.add(new Candidate(window, match, eligibility.ownership()));
            }
        }
        return new Resolution(List.copyOf(eligible), List.copyOf(unresolved));
    }

    private void validateInputs(String reactionKey, ReactionEligibilityPolicy policy) {
        if (reactionKey == null || reactionKey.isBlank()) throw new IllegalArgumentException("reactionKey is required");
        Objects.requireNonNull(policy, "policy");
    }

    @FunctionalInterface
    private interface MatchResolver {
        RuntimeReactionTriggerMatcher.TriggerMatch match(String reactorId);
    }

    public record Candidate(
            RuntimeReactionWindow window,
            RuntimeReactionTriggerMatcher.TriggerMatch triggerMatch,
            RuntimeReactionOwnershipResolver.Result ownership
    ) {
        public Candidate {
            window = Objects.requireNonNull(window, "window");
            triggerMatch = Objects.requireNonNull(triggerMatch, "triggerMatch");
            ownership = Objects.requireNonNull(ownership, "ownership");
            if (!ownership.ownsReaction()) throw new IllegalArgumentException("eligible candidate must own the reaction");
            if (!window.reactorId().equals(triggerMatch.reactorId())
                    || !window.triggeringActorId().equals(triggerMatch.triggeringActorId())
                    || window.triggerKind() != triggerMatch.trigger().kind()) {
                throw new IllegalArgumentException("reaction window must bind the same trigger match");
            }
        }
        public String reactorId() { return window.reactorId(); }
        public String triggeringActorId() { return window.triggeringActorId(); }
    }

    public record UnresolvedCandidate(
            RuntimeReactionWindow window,
            RuntimeReactionTriggerMatcher.TriggerMatch triggerMatch,
            RuntimeReactionOwnershipResolver.Result ownership
    ) {
        public UnresolvedCandidate {
            window = Objects.requireNonNull(window, "window");
            triggerMatch = Objects.requireNonNull(triggerMatch, "triggerMatch");
            ownership = Objects.requireNonNull(ownership, "ownership");
            if (ownership.status() != RuntimeReactionOwnershipResolver.Status.UNKNOWN) {
                throw new IllegalArgumentException("unresolved candidate requires UNKNOWN ownership");
            }
            if (!window.reactorId().equals(triggerMatch.reactorId())
                    || !window.triggeringActorId().equals(triggerMatch.triggeringActorId())
                    || window.triggerKind() != triggerMatch.trigger().kind()) {
                throw new IllegalArgumentException("reaction window must bind the same trigger match");
            }
        }
        public String reactorId() { return window.reactorId(); }
    }

    public record Resolution(List<Candidate> eligible, List<UnresolvedCandidate> unresolved) {
        public Resolution {
            eligible = eligible == null ? List.of() : List.copyOf(eligible);
            unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
        }
    }
}
