package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Language-neutral instruction for one server-owned status mutation plus its semantic event.
 *
 * <p>Ability, item, Feature, terrain, and move-special registries can plan these instructions
 * without owning status storage. The runtime executor applies them to authoritative battle state
 * and emits ordered battle events.</p>
 */
public record StatusEffectInstruction(
        String targetId,
        String status,
        int durationRounds,
        String source,
        String sourceId,
        String eventAbility,
        String eventEffect,
        String eventDescription
) {
    public StatusEffectInstruction {
        targetId = required(targetId, "targetId");
        status = required(status, "status");
        source = required(source, "source");
        sourceId = required(sourceId, "sourceId");
        eventAbility = required(eventAbility, "eventAbility");
        eventEffect = required(eventEffect, "eventEffect");
        eventDescription = required(eventDescription, "eventDescription");
        if (durationRounds <= 0) {
            throw new IllegalArgumentException("durationRounds must be positive");
        }
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }
}
