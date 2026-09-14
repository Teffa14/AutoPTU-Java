package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;

import java.util.Objects;

/**
 * Immutable identity for one discovered reaction opportunity.
 *
 * <p>The window binds the reaction, reactor, triggering actor, trigger family, battle round,
 * and authoritative semantic event fingerprint. It carries no execution authority: commit,
 * action-resource consumption, RNG, targeting, and reaction execution remain separate runtime
 * transitions.</p>
 *
 * <p>The current event component uses {@link BattleEvent#stableKey()}. That key is deterministic
 * for the same semantic event payload. A future battle-event sequence identifier can replace or
 * extend this fingerprint if the runtime needs to distinguish repeated identical events within the
 * same round; callers must not infer execution from the key alone.</p>
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

    public static RuntimeReactionWindow from(
            String reactionKey,
            int round,
            RuntimeReactionTriggerMatcher.TriggerMatch match,
            BattleEvent triggeringEvent
    ) {
        Objects.requireNonNull(match, "trigger match");
        Objects.requireNonNull(triggeringEvent, "triggering event");
        String normalizedReactionKey = MoveReactionOwnershipSource.normalizeKey(reactionKey);
        if (normalizedReactionKey.isBlank()) {
            throw new IllegalArgumentException("reactionKey is required");
        }
        String eventKey = triggeringEvent.stableKey();
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
