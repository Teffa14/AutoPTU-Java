package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.BattleEventOccurrence;

import java.util.Objects;

/**
 * Immutable identity for one discovered reaction opportunity.
 *
 * <p>The window binds the reaction, reactor, triggering actor, trigger family, battle round,
 * and authoritative event identity. It carries no execution authority: commit, action-resource
 * consumption, RNG, targeting, and reaction execution remain separate runtime transitions.</p>
 */
public record RuntimeReactionWindow(
        String windowKey,
        String reactionKey,
        int round,
        String reactorId,
        String triggeringActorId,
        RuntimeReactionTriggerRegistry.TriggerKind triggerKind,
        String triggeringEventKey
) {
    public RuntimeReactionWindow {
        if (windowKey == null || windowKey.isBlank()) {
            throw new IllegalArgumentException("windowKey is required");
        }
        if (reactionKey == null || reactionKey.isBlank()) {
            throw new IllegalArgumentException("reactionKey is required");
        }
        if (round < 0) {
            throw new IllegalArgumentException("round cannot be negative");
        }
        if (reactorId == null || reactorId.isBlank()) {
            throw new IllegalArgumentException("reactorId is required");
        }
        if (triggeringActorId == null || triggeringActorId.isBlank()) {
            throw new IllegalArgumentException("triggeringActorId is required");
        }
        triggerKind = Objects.requireNonNull(triggerKind, "triggerKind");
        if (triggeringEventKey == null || triggeringEventKey.isBlank()) {
            throw new IllegalArgumentException("triggeringEventKey is required");
        }
    }

    /**
     * Legacy semantic-payload identity. Prefer the occurrence overload once an event has entered
     * the authoritative battle event stream.
     */
    public static RuntimeReactionWindow from(
            String reactionKey,
            int round,
            RuntimeReactionTriggerMatcher.TriggerMatch match,
            BattleEvent triggeringEvent
    ) {
        Objects.requireNonNull(triggeringEvent, "triggering event");
        return fromEventKey(reactionKey, round, match, triggeringEvent.stableKey());
    }

    /**
     * Builds a window from one battle-local event occurrence. Reusing the same occurrence produces
     * the same window identity; two identical semantic events with different sequence numbers do not.
     */
    public static RuntimeReactionWindow from(
            String reactionKey,
            int round,
            RuntimeReactionTriggerMatcher.TriggerMatch match,
            BattleEventOccurrence triggeringOccurrence
    ) {
        Objects.requireNonNull(triggeringOccurrence, "triggering occurrence");
        return fromEventKey(reactionKey, round, match, triggeringOccurrence.occurrenceKey());
    }

    private static RuntimeReactionWindow fromEventKey(
            String reactionKey,
            int round,
            RuntimeReactionTriggerMatcher.TriggerMatch match,
            String eventKey
    ) {
        Objects.requireNonNull(match, "trigger match");
        String normalizedReactionKey = MoveReactionOwnershipSource.normalizeKey(reactionKey);
        if (normalizedReactionKey.isBlank()) {
            throw new IllegalArgumentException("reactionKey is required");
        }
        String windowKey = "round=" + round
                + "|reaction=" + normalizedReactionKey
                + "|reactor=" + match.reactorId()
                + "|actor=" + match.triggeringActorId()
                + "|trigger=" + match.trigger().kind().name()
                + "|event=" + eventKey;
        return new RuntimeReactionWindow(
                windowKey,
                normalizedReactionKey,
                round,
                match.reactorId(),
                match.triggeringActorId(),
                match.trigger().kind(),
                eventKey
        );
    }
}
