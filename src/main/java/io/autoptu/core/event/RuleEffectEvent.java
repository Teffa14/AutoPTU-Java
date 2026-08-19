package io.autoptu.core.event;

import java.util.Locale;

/**
 * Semantic playback event for an authoritative rule source such as an item,
 * ability, status, terrain effect, or Trainer Feature.
 *
 * Adapters render this event; they do not use it to recompute battle results.
 */
public record RuleEffectEvent(
        String sourceKind,
        String sourceName,
        String actorId,
        String targetId,
        String moveId,
        String effect,
        double amount,
        int actorHp
) implements BattleEvent {
    public RuleEffectEvent {
        sourceKind = safe(sourceKind).toLowerCase(Locale.ROOT);
        sourceName = safe(sourceName);
        actorId = safe(actorId);
        targetId = safe(targetId);
        moveId = safe(moveId);
        effect = safe(effect);
        if (sourceKind.isBlank()) throw new IllegalArgumentException("sourceKind is required");
        if (sourceName.isBlank()) throw new IllegalArgumentException("sourceName is required");
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (effect.isBlank()) throw new IllegalArgumentException("effect is required");
        if (!Double.isFinite(amount)) throw new IllegalArgumentException("amount must be finite");
        if (actorHp < 0) throw new IllegalArgumentException("actorHp cannot be negative");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.RULE_EFFECT;
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(), sourceKind, sourceName.toLowerCase(Locale.ROOT), actorId,
                targetId, moveId, effect, Double.toString(amount), Integer.toString(actorHp));
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
