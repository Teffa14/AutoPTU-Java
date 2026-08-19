package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable server-owned temporary-effect multiset for one combatant.
 *
 * Python permits multiple temporary effects with the same name and several lifecycle
 * paths remove every matching entry with repeated remove_temporary_effect calls. This
 * store preserves that multiplicity while keeping Minecraft/Cobblemon outside the rule.
 */
public final class TemporaryEffectStore {
    private final LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();

    public void add(String effectName) {
        String key = normalize(effectName);
        counts.merge(key, 1, Integer::sum);
    }

    public boolean has(String effectName) {
        return count(effectName) > 0;
    }

    public int count(String effectName) {
        String key = normalize(effectName);
        return counts.getOrDefault(key, 0);
    }

    /** Remove every occurrence and return the number removed. */
    public int removeAll(String effectName) {
        String key = normalize(effectName);
        Integer removed = counts.remove(key);
        return removed == null ? 0 : removed;
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(counts);
    }

    public List<String> namesInInsertionOrder() {
        return List.copyOf(new ArrayList<>(counts.keySet()));
    }

    private static String normalize(String effectName) {
        if (effectName == null || effectName.isBlank()) {
            throw new IllegalArgumentException("effectName is required");
        }
        return effectName.strip().toLowerCase(Locale.ROOT);
    }
}
