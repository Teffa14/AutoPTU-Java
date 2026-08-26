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
 * Immutable server-owned Chronicler archive metadata.
 *
 * Python materializes this data from Trainer class state. Minecraft/Cobblemon may render the
 * archives and records, but battle rules must not accept Chronicler metadata from action input.
 */
public final class ChroniclerProfileMetadata {
    private static final List<String> RECORD_KINDS = List.of("profile", "technique", "travel");
    private static final Map<String, String> ARCHIVE_ALIASES = Map.of(
            "profile", "profile",
            "profile album", "profile",
            "technique", "technique",
            "technique album", "technique",
            "travel", "travel",
            "travel album", "travel"
    );
    private static final Map<String, String> TRAVEL_ABILITY_ALIASES = Map.of(
            "keen eye", "Keen Eye",
            "keeneye", "Keen Eye",
            "perception", "Perception"
    );
    private static final ChroniclerProfileMetadata EMPTY = new ChroniclerProfileMetadata(Set.of(), Map.of(), "");

    private final Set<String> archives;
    private final Map<String, List<String>> records;
    private final String travelAbility;

    public ChroniclerProfileMetadata(Collection<String> archives, Map<String, ? extends Collection<String>> records) {
        this(archives, records, "");
    }

    public ChroniclerProfileMetadata(
            Collection<String> archives,
            Map<String, ? extends Collection<String>> records,
            String travelAbility
    ) {
        LinkedHashSet<String> archiveCopy = new LinkedHashSet<>();
        if (archives != null) {
            for (String archive : archives) {
                String normalized = normalizeArchive(archive);
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
                    String label = normalizeRecordName(value);
                    String key = label.toLowerCase(Locale.ROOT);
                    if (key.isEmpty() || !seen.add(key)) continue;
                    copied.add(label);
                }
            }
            recordCopy.put(kind, List.copyOf(copied));
        }
        this.records = Collections.unmodifiableMap(recordCopy);
        this.travelAbility = normalizeTravelAbility(travelAbility);
    }

    public static ChroniclerProfileMetadata empty() {
        return EMPTY;
    }

    public Set<String> archives() {
        return archives;
    }

    public boolean hasArchive(String archive) {
        return archives.contains(normalizeArchive(archive));
    }

    public List<String> records(String kind) {
        if (kind == null) return List.of();
        return records.getOrDefault(kind.strip().toLowerCase(Locale.ROOT), List.of());
    }

    public Map<String, List<String>> records() {
        return records;
    }

    public String travelAbility() {
        return travelAbility;
    }

    private static String normalizeArchive(String value) {
        if (value == null) return "";
        return ARCHIVE_ALIASES.getOrDefault(value.strip().toLowerCase(Locale.ROOT), "");
    }

    private static String normalizeRecordName(String value) {
        if (value == null) return "";
        return value.strip().replaceAll("\\s+", " ");
    }

    private static String normalizeTravelAbility(String value) {
        if (value == null) return "";
        return TRAVEL_ABILITY_ALIASES.getOrDefault(value.strip().toLowerCase(Locale.ROOT), "");
    }
}
