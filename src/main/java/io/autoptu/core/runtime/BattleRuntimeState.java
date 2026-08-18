package io.autoptu.core.runtime;

import io.autoptu.core.model.MovementGrid;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Minimal authoritative battle state used by headless simulation and Minecraft adapters. */
public final class BattleRuntimeState {
    private final MovementGrid grid;
    private final LinkedHashMap<String, RuntimeCombatantState> combatants = new LinkedHashMap<>();
    private final LinkedHashMap<String, Set<String>> statusesByCombatant = new LinkedHashMap<>();

    public BattleRuntimeState(MovementGrid grid, List<RuntimeCombatantState> combatants) {
        this(grid, combatants, Map.of());
    }

    /**
     * Materializes canonical status names alongside combatants. Minecraft/Cobblemon
     * may project these statuses visually, but battle resolution reads this server-
     * owned snapshot rather than client/entity claims.
     */
    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant
    ) {
        if (grid == null) {
            throw new IllegalArgumentException("grid is required");
        }
        this.grid = grid;
        for (RuntimeCombatantState combatant : combatants == null ? List.<RuntimeCombatantState>of() : combatants) {
            if (combatant == null) {
                continue;
            }
            RuntimeCombatantState previous = this.combatants.putIfAbsent(combatant.combatantId(), combatant);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate combatantId: " + combatant.combatantId());
            }
        }
        if (statusesByCombatant != null) {
            for (Map.Entry<String, ? extends Collection<String>> entry : statusesByCombatant.entrySet()) {
                String combatantId = entry.getKey();
                if (!this.combatants.containsKey(combatantId)) {
                    throw new IllegalArgumentException("status state references unknown combatant: " + combatantId);
                }
                Set<String> normalized = normalizeStatuses(entry.getValue());
                if (!normalized.isEmpty()) {
                    this.statusesByCombatant.put(combatantId, normalized);
                }
            }
        }
    }

    public MovementGrid grid() {
        return grid;
    }

    public RuntimeCombatantState requireCombatant(String combatantId) {
        RuntimeCombatantState combatant = combatants.get(combatantId);
        if (combatant == null) {
            throw new IllegalArgumentException("unknown combatant: " + combatantId);
        }
        return combatant;
    }

    public Map<String, RuntimeCombatantState> combatants() {
        return Map.copyOf(combatants);
    }

    public Set<String> statuses(String combatantId) {
        requireCombatant(combatantId);
        return statusesByCombatant.getOrDefault(combatantId, Set.of());
    }

    public boolean hasStatus(String combatantId, String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        return statuses(combatantId).contains(status.strip().toLowerCase(Locale.ROOT));
    }

    private static Set<String> normalizeStatuses(Collection<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String status : statuses) {
            if (status == null || status.isBlank()) {
                continue;
            }
            normalized.add(status.strip().toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }
}
