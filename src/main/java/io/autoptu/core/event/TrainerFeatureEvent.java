package io.autoptu.core.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Semantic Trainer Feature event for headless playback adapters such as Minecraft. */
public record TrainerFeatureEvent(
        String actorId,
        String feature,
        String effect,
        Map<String, Object> details
) implements BattleEvent {
    public TrainerFeatureEvent {
        actorId = safe(actorId);
        feature = safe(feature);
        effect = safe(effect);
        details = immutableScalarDetails(details);
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (feature.isBlank()) throw new IllegalArgumentException("feature is required");
        if (effect.isBlank()) throw new IllegalArgumentException("effect is required");
        if (targetHp() < 0) throw new IllegalArgumentException("targetHp cannot be negative");
    }

    /**
     * Transitional constructor retained for previously ported status-skip Feature events.
     * New perk hooks should use the metadata-bearing constructor above.
     */
    public TrainerFeatureEvent(
            String actorId,
            String feature,
            String effect,
            String move,
            String status,
            int targetHp
    ) {
        this(actorId, feature, effect, Map.of(
                "move", safe(move),
                "status", safe(status),
                "targetHp", targetHp
        ));
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.TRAINER_FEATURE;
    }

    public String move() {
        return textDetail("move");
    }

    public String status() {
        return textDetail("status");
    }

    public int targetHp() {
        return intDetail("targetHp", 0);
    }

    public String trainer() {
        return textDetail("trainer");
    }

    public String phase() {
        return textDetail("phase");
    }

    public String stat() {
        return textDetail("stat");
    }

    public String description() {
        return textDetail("description");
    }

    public int amount() {
        return intDetail("amount", 0);
    }

    public int apSpent() {
        return intDetail("ap_spent", 0);
    }

    @Override
    public String stableKey() {
        String base = String.join("|",
                kind().value(), actorId, feature.toLowerCase(Locale.ROOT), effect,
                move(), status().toLowerCase(Locale.ROOT), Integer.toString(targetHp()));
        TreeMap<String, Object> extras = new TreeMap<>(details);
        extras.remove("move");
        extras.remove("status");
        extras.remove("targetHp");
        if (extras.isEmpty()) return base;
        StringBuilder key = new StringBuilder(base);
        for (Map.Entry<String, Object> entry : extras.entrySet()) {
            key.append('|').append(entry.getKey()).append('=').append(String.valueOf(entry.getValue()));
        }
        return key.toString();
    }

    private String textDetail(String key) {
        Object value = details.get(key);
        return value == null ? "" : String.valueOf(value).strip();
    }

    private int intDetail(String key, int fallback) {
        Object value = details.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.strip());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static Map<String, Object> immutableScalarDetails(Map<String, ?> source) {
        if (source == null || source.isEmpty()) return Map.of();
        LinkedHashMap<String, Object> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Trainer Feature detail keys must be non-blank");
            }
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Integer)
                    && !(value instanceof Long)
                    && !(value instanceof Double)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException(
                        "Trainer Feature detail values must be scalar: " + key
                );
            }
            copied.put(key.strip(), value);
        }
        return Collections.unmodifiableMap(copied);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
