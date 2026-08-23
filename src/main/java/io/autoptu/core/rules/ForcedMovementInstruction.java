package io.autoptu.core.rules;

/**
 * Language-neutral forced-movement intent extracted from PTU move metadata.
 * This contract does not move a combatant; it only freezes the Push/Pull
 * instruction that later spatial resolution must execute authoritatively.
 */
public record ForcedMovementInstruction(Kind kind, int distance) {
    public ForcedMovementInstruction {
        if (kind == null) throw new IllegalArgumentException("kind is required");
        if (distance < 1) throw new IllegalArgumentException("distance must be >= 1");
    }

    public enum Kind {
        PUSH,
        PULL
    }
}
