package io.autoptu.core.runtime;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable server-owned lookup boundary for canonical held-item rule profiles.
 *
 * Lookup mirrors Python item_catalog.get_item_entry(): exact normalized name first,
 * then a punctuation/whitespace-insensitive compact-key fallback in insertion order.
 */
public final class HeldItemRuleCatalog {
    private final LinkedHashMap<String, HeldItemStartRuleProfile> profilesByName = new LinkedHashMap<>();

    public HeldItemRuleCatalog(Map<String, HeldItemStartRuleProfile> profilesByName) {
        if (profilesByName == null) return;
        for (Map.Entry<String, HeldItemStartRuleProfile> entry : profilesByName.entrySet()) {
            String normalized = normalize(entry.getKey());
            if (normalized.isBlank() || entry.getValue() == null) continue;
            this.profilesByName.put(normalized, entry.getValue());
        }
    }

    public Optional<HeldItemStartRuleProfile> find(HeldItemState item) {
        if (item == null) return Optional.empty();
        return findByName(item.name());
    }

    public Optional<HeldItemStartRuleProfile> findByName(String itemName) {
        String normalized = normalize(itemName);
        if (normalized.isBlank()) return Optional.empty();
        HeldItemStartRuleProfile exact = profilesByName.get(normalized);
        if (exact != null) return Optional.of(exact);

        String compact = compact(normalized);
        if (compact.isBlank()) return Optional.empty();
        for (Map.Entry<String, HeldItemStartRuleProfile> entry : profilesByName.entrySet()) {
            if (compact(entry.getKey()).equals(compact)) return Optional.of(entry.getValue());
        }
        return Optional.empty();
    }

    public Map<String, HeldItemStartRuleProfile> snapshot() {
        return Map.copyOf(profilesByName);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String compact(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) out.append(ch);
        }
        return out.toString();
    }
}
