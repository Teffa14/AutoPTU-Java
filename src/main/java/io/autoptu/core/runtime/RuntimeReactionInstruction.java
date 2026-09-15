package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;

import java.util.Objects;

/**
 * Immutable server-authoritative instruction produced after a reaction window commits.
 *
 * <p>The instruction freezes reaction identity, reactor, target, triggering occurrence and the
 * canonical move definition to execute. It performs no targeting rolls, RNG, damage, mutation or
 * adapter work; those remain later core transitions.</p>
 */
public record RuntimeReactionInstruction(
        String instructionKey,
        String windowKey,
        String reactionKey,
        int round,
        String reactorId,
        String targetCombatantId,
        RuntimeReactionTriggerRegistry.TriggerKind triggerKind,
        String triggeringEventKey,
        MoveOption move
) {
    public RuntimeReactionInstruction {
        instructionKey = requireText(instructionKey, "instructionKey");
        windowKey = requireText(windowKey, "windowKey");
        reactionKey = requireText(reactionKey, "reactionKey");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
        reactorId = requireText(reactorId, "reactorId");
        targetCombatantId = requireText(targetCombatantId, "targetCombatantId");
        triggerKind = Objects.requireNonNull(triggerKind, "triggerKind");
        triggeringEventKey = requireText(triggeringEventKey, "triggeringEventKey");
        move = Objects.requireNonNull(move, "move");
    }

    public static RuntimeReactionInstruction from(RuntimeReactionWindow window, MoveOption move) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(move, "move");
        return new RuntimeReactionInstruction(
                "reaction|" + window.windowKey(),
                window.windowKey(),
                window.reactionKey(),
                window.round(),
                window.reactorId(),
                window.triggeringActorId(),
                window.triggerKind(),
                window.triggeringEventKey(),
                move
        );
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.strip();
    }
}
