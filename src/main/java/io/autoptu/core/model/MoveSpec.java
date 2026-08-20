package io.autoptu.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Language-neutral move metadata used by targeting and generic move-trait rules.
 * Additional PTU fields should be added only when a parity-tested subsystem needs them.
 */
public record MoveSpec(
        String targetKind,
        String rangeKind,
        Integer targetRange,
        Integer rangeValue,
        String areaKind,
        Integer areaValue,
        String rangeText,
        List<String> keywords
) {
    public MoveSpec {
        keywords = normalizeKeywords(keywords);
    }

    /** Backwards-compatible constructor for older targeting/movement callers. */
    public MoveSpec(
            String targetKind,
            String rangeKind,
            Integer targetRange,
            Integer rangeValue,
            String areaKind,
            Integer areaValue,
            String rangeText
    ) {
        this(targetKind, rangeKind, targetRange, rangeValue, areaKind, areaValue, rangeText, List.of());
    }

    /**
     * Python move_traits.move_has_keyword parity: exact keyword identity after
     * trimming and case normalization. Substrings do not count.
     */
    public boolean hasKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return false;
        String normalized = keyword.strip().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) return false;
        for (String entry : keywords) {
            if (entry.toLowerCase(Locale.ROOT).equals(normalized)) return true;
        }
        return false;
    }

    private static List<String> normalizeKeywords(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        ArrayList<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            normalized.add(value.strip());
        }
        return List.copyOf(normalized);
    }
}
