package io.autoptu.core.event;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Semantic Ability event emitted by the authoritative battle runtime. */
public record AbilityEvent(
        String actorId,
        String ability,
        String effect,
        Map<String, Object> details
) implements BattleEvent {
    public AbilityEvent {
        actorId = safe(actorId);
        ability = safe(ability);
        effect = safe(effect);
        details = immutableScalarDetails(details);
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (ability.isBlank()) throw new IllegalArgumentException("ability is required");
        if (effect.isBlank()) throw new IllegalArgumentException("effect is required");
        if (targetHp() < 0) throw new IllegalArgumentException("targetHp cannot be negative");
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.ABILITY;
    }

    public String target() {
        return textDetail("target");
    }

    public String description() {
        return textDetail("description");
    }

    public int targetHp() {
        return intDetail(details, "targetHp", 0);
    }

    @Override
    public String stableKey() {
        String base = String.join("|",
                kind().value(), actorId, ability.toLowerCase(Locale.ROOT), effect,
                target(), Integer.toString(targetHp()));
        TreeMap<String, Object> extras = new TreeMap<>(details);
        extras.remove("target");
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

    private static int intDetail(Map<String, Object> details, String key, int fallback) {
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
                throw new IllegalArgumentException("Ability detail keys must be non-blank");
            }
            Object value = entry.getValue();
            if (value != null
                    && !(value instanceof String)
                    && !(value instanceof Integer)
                    && !(value instanceof Long)
                    && !(value instanceof Double)
                    && !(value instanceof Boolean)) {
                throw new IllegalArgumentException("Ability detail values must be scalar: " + key);
            }
            copied.put(key.strip(), value);
        }
        return Collections.unmodifiableMap(copied);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
