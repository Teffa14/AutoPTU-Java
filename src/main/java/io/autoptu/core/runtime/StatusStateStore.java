package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable server-authoritative status state keyed by stable combatant id.
 *
 * This store separates rule-bearing status metadata from Minecraft entity data. It
 * preserves deterministic insertion order while keeping at most one entry per
 * normalized status name for each combatant, matching the existing set semantics.
 */
public final class StatusStateStore {
    private final LinkedHashMap<String, LinkedHashMap<String, StatusEntry>> byCombatant = new LinkedHashMap<>();

    public void replace(String combatantId, Collection<StatusEntry> entries) {
        String id = requireCombatantId(combatantId);
        LinkedHashMap<String, StatusEntry> normalized = new LinkedHashMap<>();
        if (entries != null) {
            for (StatusEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                StatusEntry previous = normalized.putIfAbsent(entry.name(), entry);
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate status for combatant " + id + ": " + entry.name());
                }
            }
        }
        if (normalized.isEmpty()) {
            byCombatant.remove(id);
        } else {
            byCombatant.put(id, normalized);
        }
    }

    public void replaceNames(String combatantId, Collection<String> names) {
        ArrayList<StatusEntry> entries = new ArrayList<>();
        if (names != null) {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                String normalized = name.strip().toLowerCase(Locale.ROOT);
                if (seen.add(normalized)) {
                    entries.add(new StatusEntry(normalized));
                }
            }
        }
        replace(combatantId, entries);
    }

    public void put(String combatantId, StatusEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("status entry is required");
        }
        String id = requireCombatantId(combatantId);
        byCombatant.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(entry.name(), entry);
    }

    public boolean remove(String combatantId, String statusName) {
        String id = requireCombatantId(combatantId);
        String name = normalizeStatusName(statusName);
        LinkedHashMap<String, StatusEntry> entries = byCombatant.get(id);
        if (entries == null) {
            return false;
        }
        boolean removed = entries.remove(name) != null;
        if (entries.isEmpty()) {
            byCombatant.remove(id);
        }
        return removed;
    }

    public boolean has(String combatantId, String statusName) {
        return find(combatantId, statusName).isPresent();
    }

    public Optional<StatusEntry> find(String combatantId, String statusName) {
        String id = requireCombatantId(combatantId);
        String name = normalizeStatusName(statusName);
        LinkedHashMap<String, StatusEntry> entries = byCombatant.get(id);
        return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(name));
    }

    public List<StatusEntry> entries(String combatantId) {
        String id = requireCombatantId(combatantId);
        LinkedHashMap<String, StatusEntry> entries = byCombatant.get(id);
        return entries == null ? List.of() : List.copyOf(entries.values());
    }

    public Set<String> names(String combatantId) {
        String id = requireCombatantId(combatantId);
        LinkedHashMap<String, StatusEntry> entries = byCombatant.get(id);
        return entries == null ? Set.of() : Set.copyOf(entries.keySet());
    }

    private static String requireCombatantId(String combatantId) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        return combatantId;
    }

    private static String normalizeStatusName(String statusName) {
        if (statusName == null || statusName.isBlank()) {
            throw new IllegalArgumentException("status name is required");
        }
        return statusName.strip().toLowerCase(Locale.ROOT);
    }
}
