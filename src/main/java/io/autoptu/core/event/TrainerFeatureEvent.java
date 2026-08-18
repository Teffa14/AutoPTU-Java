package io.autoptu.core.event;

import java.util.Locale;

/** Semantic Trainer Feature event for headless playback adapters such as Minecraft. */
public record TrainerFeatureEvent(
        String actorId,
        String feature,
        String effect,
        String move,
        String status,
        int targetHp
) implements BattleEvent {
    public TrainerFeatureEvent {
        actorId = safe(actorId);
        feature = safe(feature);
        effect = safe(effect);
        move = safe(move);
        status = safe(status);
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (feature.isBlank()) throw new IllegalArgumentException("feature is required");
        if (effect.isBlank()) throw new IllegalArgumentException("effect is required");
        if (targetHp < 0) throw new IllegalArgumentException("targetHp cannot be negative");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.TRAINER_FEATURE;
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(), actorId, feature.toLowerCase(Locale.ROOT), effect,
                move, status.toLowerCase(Locale.ROOT), Integer.toString(targetHp));
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
