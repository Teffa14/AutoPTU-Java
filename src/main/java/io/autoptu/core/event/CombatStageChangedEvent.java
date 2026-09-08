package io.autoptu.core.event;

import io.autoptu.core.model.CombatStageStat;

import java.util.Locale;

/**
 * Semantic event for one authoritative combat-stage mutation.
 *
 * <p>The core owns the stage calculation and emits the resulting value. Minecraft,
 * Cobblemon, and other adapters only render this event and never recalculate the
 * requested delta, prevention result, clamp, or final stage.</p>
 */
public record CombatStageChangedEvent(
        String actorId,
        String targetId,
        String moveId,
        CombatStageStat stat,
        String effect,
        int amount,
        int newStage,
        String description,
        int targetHp,
        int round,
        String phase
) implements BattleEvent {
    public CombatStageChangedEvent {
        actorId = required(actorId, "actorId");
        targetId = required(targetId, "targetId");
        moveId = safe(moveId);
        if (stat == null) throw new IllegalArgumentException("stat is required");
        effect = required(effect, "effect");
        description = safe(description);
        phase = safe(phase).toLowerCase(Locale.ROOT);
        if (targetHp < 0) throw new IllegalArgumentException("targetHp cannot be negative");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.COMBAT_STAGE;
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(), actorId, targetId, moveId,
                stat.name().toLowerCase(Locale.ROOT), effect,
                Integer.toString(amount), Integer.toString(newStage),
                description, Integer.toString(targetHp), Integer.toString(round), phase);
    }

    private static String required(String value, String field) {
        String canonical = safe(value);
        if (canonical.isBlank()) throw new IllegalArgumentException(field + " is required");
        return canonical;
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
