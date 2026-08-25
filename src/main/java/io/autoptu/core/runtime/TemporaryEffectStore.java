package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

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
            if (entry.name().equals(key)) count += 1;
        }
        return count;
    }

    public List<TemporaryEffectEntry> getAll(String effectName) {
        String key = normalize(effectName);
        ArrayList<TemporaryEffectEntry> matching = new ArrayList<>();
        for (TemporaryEffectEntry entry : entries) {
            if (entry.name().equals(key)) matching.add(entry);
        }
        return List.copyOf(matching);
    }

    public List<TemporaryEffectEntry> entriesInInsertionOrder() {
        return List.copyOf(entries);
    }

    /** Remove the first occurrence, matching Python PokemonState.remove_temporary_effect. */
    public boolean removeFirst(String effectName) {
        String key = normalize(effectName);
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).name().equals(key)) {
                entries.remove(index);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove the first entry equal to the supplied snapshot entry.
     *
     * Python frequently iterates list(temporary_effects) and then calls list.remove(entry),
     * which removes the first equal dictionary rather than every entry in the family. This
     * boundary preserves that exact behavior for metadata-sensitive expiry rules.
     */
    public boolean removeEntry(TemporaryEffectEntry expected) {
        if (expected == null) return false;
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).equals(expected)) {
                entries.remove(index);
                return true;
            }
        }
        return false;
    }

    /** Remove every occurrence and return the number removed. */
    public int removeAll(String effectName) {
        return removeIf(effectName, ignored -> true);
    }

    /** Remove only matching occurrences of one normalized effect family. */
    public int removeIf(String effectName, Predicate<TemporaryEffectEntry> predicate) {
        String key = normalize(effectName);
        if (predicate == null) throw new IllegalArgumentException("predicate is required");
        int before = entries.size();
        entries.removeIf(entry -> entry.name().equals(key) && predicate.test(entry));
        return before - entries.size();
    }

    /** Compatibility count snapshot used by existing diagnostics/tests. */
    public Map<String, Integer> snapshot() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (TemporaryEffectEntry entry : entries) counts.merge(entry.name(), 1, Integer::sum);
        return Map.copyOf(counts);
    }

    public List<String> namesInInsertionOrder() {
        LinkedHashMap<String, Boolean> names = new LinkedHashMap<>();
        for (TemporaryEffectEntry entry : entries) names.putIfAbsent(entry.name(), Boolean.TRUE);
        return List.copyOf(new ArrayList<>(names.keySet()));
    }

    private static String normalize(String effectName) {
        if (effectName == null || effectName.isBlank()) throw new IllegalArgumentException("effectName is required");
        return effectName.strip().toLowerCase(Locale.ROOT);
    }
}
