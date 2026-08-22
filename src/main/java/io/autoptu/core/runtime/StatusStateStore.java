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
 * Python AutoPTU stores statuses as an ordered list and can retain multiple entries
 * with the same normalized name when a rule explicitly allows stacking. This store
 * preserves that multiplicity and insertion order while legacy name-based views
 * continue to expose unique normalized names.
 */
public final class StatusStateStore {
    private final LinkedHashMap<String, ArrayList<StatusEntry>> byCombatant = new LinkedHashMap<>();

    public void replace(String combatantId, Collection<StatusEntry> entries) {
        String id = requireCombatantId(combatantId);
        ArrayList<StatusEntry> copied = new ArrayList<>();
        if (entries != null) {
            for (StatusEntry entry : entries) {
                if (entry != null) {
                    copied.add(entry);
                }
            }
        }
        if (copied.isEmpty()) {
            byCombatant.remove(id);
        } else {
            byCombatant.put(id, copied);
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

    /**
     * Replace the first matching entry in place, or append one when absent.
     * Additional stacked entries with the same name remain untouched.
     */
    public void put(String combatantId, StatusEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("status entry is required");
        }
        String id = requireCombatantId(combatantId);
        ArrayList<StatusEntry> entries = byCombatant.computeIfAbsent(id, ignored -> new ArrayList<>());
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).name().equals(entry.name())) {
                entries.set(index, entry);
                return;
            }
        }
        entries.add(entry);
    }

    /** Append a new entry even when the normalized status name already exists. */
    public void append(String combatantId, StatusEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("status entry is required");
        }
        String id = requireCombatantId(combatantId);
        byCombatant.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entry);
    }

    /** Remove only the first matching entry, preserving later stacked entries. */
    public boolean remove(String combatantId, String statusName) {
        String id = requireCombatantId(combatantId);
        String name = normalizeStatusName(statusName);
        ArrayList<StatusEntry> entries = byCombatant.get(id);
        if (entries == null) {
            return false;
        }
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).name().equals(name)) {
                entries.remove(index);
                if (entries.isEmpty()) {
                    byCombatant.remove(id);
                }
                return true;
            }
        }
        return false;
    }

    /** Remove every matching entry and return the number removed. */
    public int removeAll(String combatantId, String statusName) {
        String id = requireCombatantId(combatantId);
        String name = normalizeStatusName(statusName);
        ArrayList<StatusEntry> entries = byCombatant.get(id);
        if (entries == null) {
            return 0;
        }
        int before = entries.size();
        entries.removeIf(entry -> entry.name().equals(name));
        int removed = before - entries.size();
        if (entries.isEmpty()) {
            byCombatant.remove(id);
        }
        return removed;
    }

    /** Remove all statuses for one combatant and return how many entries existed. */
    public int clear(String combatantId) {
        String id = requireCombatantId(combatantId);
        ArrayList<StatusEntry> removed = byCombatant.remove(id);
        return removed == null ? 0 : removed.size();
    }

    public boolean has(String combatantId, String statusName) {
        return find(combatantId, statusName).isPresent();
    }

    /** Return the first matching status, matching Python's ordered status scan. */
    public Optional<StatusEntry> find(String combatantId, String statusName) {
        String id = requireCombatantId(combatantId);
        String name = normalizeStatusName(statusName);
        ArrayList<StatusEntry> entries = byCombatant.get(id);
        if (entries == null) {
            return Optional.empty();
        }
        return entries.stream().filter(entry -> entry.name().equals(name)).findFirst();
    }

    public List<StatusEntry> findAll(String combatantId, String statusName) {
        String id = requireCombatantId(combatantId);
        String name = normalizeStatusName(statusName);
        ArrayList<StatusEntry> entries = byCombatant.get(id);
        if (entries == null) {
            return List.of();
        }
        return entries.stream().filter(entry -> entry.name().equals(name)).toList();
    }

    public List<StatusEntry> entries(String combatantId) {
        String id = requireCombatantId(combatantId);
        ArrayList<StatusEntry> entries = byCombatant.get(id);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public Set<String> names(String combatantId) {
        String id = requireCombatantId(combatantId);
        ArrayList<StatusEntry> entries = byCombatant.get(id);
        if (entries == null) {
            return Set.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (StatusEntry entry : entries) {
            names.add(entry.name());
        }
        return Set.copyOf(names);
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
