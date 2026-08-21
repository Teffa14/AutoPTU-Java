package io.autoptu.core.event;

import io.autoptu.core.runtime.FieldEffectKind;

import java.util.Locale;

/** Semantic playback event emitted when a canonical terrain, zone, or room expires. */
public record FieldEffectEndedEvent(
        FieldEffectKind fieldKind,
        String effectName,
        int round
) implements BattleEvent {
    public FieldEffectEndedEvent {
        if (fieldKind == null) throw new IllegalArgumentException("fieldKind is required");
        effectName = effectName == null ? "" : effectName.strip();
        if (effectName.isBlank()) throw new IllegalArgumentException("effectName is required");
        if (round < 0) throw new IllegalArgumentException("round cannot be negative");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.FIELD_EFFECT;
    }

    public String effect() {
        return fieldKind.wireName() + "_ends";
    }

    @Override
    public String stableKey() {
        return String.join("|",
                kind().value(),
                fieldKind.wireName(),
                effectName.toLowerCase(Locale.ROOT),
                effect(),
                Integer.toString(round));
    }
}
