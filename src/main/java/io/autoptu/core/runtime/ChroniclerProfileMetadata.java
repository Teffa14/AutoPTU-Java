package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Immutable server-owned Chronicler archive/profile metadata.
 *
 * Python materializes this data from the Chronicler Trainer Feature definition. Minecraft/Cobblemon
 * may render the records, but battle rules must not accept archive/profile claims from action input.
 */
public final class ChroniclerProfileMetadata {
    private static final List<String> RECORD_KINDS = List.of("pokemon", "move", "ability", "trainer");
    private static final ChroniclerProfileMetadata EMPTY = new ChroniclerProfileMetadata(Set.of(), Map.of());

    private final Set<String> archives;
    private final Map<String, List<String>> records;

    public ChroniclerProfileMetadata(Collection<String> archives, Map<String, ? extends Collection<String>> records) {
        LinkedHashSet<String> archiveCopy = new LinkedHashSet<>();
        if (archives != null) {
            for (String archive : archives) {
                String normalized = normalizeLower(archive);
                if (!normalized.isEmpty()) archiveCopy.add(normalized);
            }
        }
        this.archives = Collections.unmodifiableSet(archiveCopy);

        LinkedHashMap<String, List<String>> recordCopy = new LinkedHashMap<>();
        for (String kind : RECORD_KINDS) {
            Collection<String> values = records == null ? null : records.get(kind);
            ArrayList<String> copied = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String label = value == null ? "" : value.strip();
                    if (label.isEmpty()) continue;
                    if (kind.equals("pokemon") || kind.equals("ability")) {
                        String key = label.toLowerCase(Locale.ROOT);
                        if (!seen.add(key)) continue;
                    }
                    copied.add(label);
                }
            }
            recordCopy.put(kind, List.copyOf(copied));
        }
        this.records = Collections.unmodifiableMap(recordCopy);
    }

    public static ChroniclerProfileMetadata empty() {
        return EMPTY;
    }

    public Set<String> archives() {
        return archives;
    }

    public boolean hasArchive(String archive) {
        return archives.contains(normalizeLower(archive));
    }

    public List<String> records(String kind) {
        if (kind == null) return List.of();
        return records.getOrDefault(kind.strip().toLowerCase(Locale.ROOT), List.of());
    }

    public Map<String, List<String>> records() {
        return records;
    }

    private static String normalizeLower(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
