package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mutable server-owned temporary-effect collection for one combatant.
 *
 * Python permits multiple temporary effects with the same name and stores metadata on
 * each entry. This store preserves insertion order, multiplicity, and payload while
 * keeping Minecraft/Cobblemon outside the authoritative rule state.
 */
public final class TemporaryEffectStore {
    private final ArrayList<TemporaryEffectEntry> entries = new ArrayList<>();

    public void add(String effectName) {
        add(effectName, Map.of());
    }

    public void add(String effectName, Map<String, ?> payload) {
        LinkedHashMap<String, Object> copiedPayload = new LinkedHashMap<>();
        if (payload != null) {
            for (Map.Entry<String, ?> entry : payload.entrySet()) {
                copiedPayload.put(entry.getKey(), entry.getValue());
            }
        }
        entries.add(new TemporaryEffectEntry(effectName, copiedPayload));
    }

    public boolean has(String effectName) {
        return count(effectName) > 0;
    }

    public int count(String effectName) {
        String key = normalize(effectName);
        int count = 0;
        for (TemporaryEffectEntry entry : entries) {
            if (entry.name().equals(key)) {
                count += 1;
            }
        }
        return count;
    }

    public List<TemporaryEffectEntry> getAll(String effectName) {
        String key = normalize(effectName);
        ArrayList<TemporaryEffectEntry> matching = new ArrayList<>();
        for (TemporaryEffectEntry entry : entries) {
            if (entry.name().equals(key)) {
                matching.add(entry);
            }
        }
        return List.copyOf(matching);
    }

    public List<TemporaryEffectEntry> entriesInInsertionOrder() {
        return List.copyOf(entries);
    }

    /** Remove every occurrence and return the number removed. */
    public int removeAll(String effectName) {
        String key = normalize(effectName);
        int before = entries.size();
        entries.removeIf(entry -> entry.name().equals(key));
        return before - entries.size();
    }

    /** Compatibility count snapshot used by existing diagnostics/tests. */
    public Map<String, Integer> snapshot() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (TemporaryEffectEntry entry : entries) {
            counts.merge(entry.name(), 1, Integer::sum);
        }
        return Map.copyOf(counts);
    }

    public List<String> namesInInsertionOrder() {
        LinkedHashMap<String, Boolean> names = new LinkedHashMap<>();
        for (TemporaryEffectEntry entry : entries) {
            names.putIfAbsent(entry.name(), Boolean.TRUE);
        }
        return List.copyOf(new ArrayList<>(names.keySet()));
    }

    private static String normalize(String effectName) {
        if (effectName == null || effectName.isBlank()) {
            throw new IllegalArgumentException("effectName is required");
        }
        return effectName.strip().toLowerCase(Locale.ROOT);
    }
}
