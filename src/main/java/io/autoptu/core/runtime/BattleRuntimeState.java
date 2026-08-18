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
    private final LinkedHashMap<String, StatusSkipFeatureState> statusSkipFeaturesByCombatant = new LinkedHashMap<>();

    public BattleRuntimeState(MovementGrid grid, List<RuntimeCombatantState> combatants) {
        this(grid, combatants, Map.of(), Map.of());
    }

    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant
    ) {
        this(grid, combatants, statusesByCombatant, Map.of());
    }

    /**
     * Materializes canonical status names and Trainer Feature state alongside combatants.
     * Minecraft/Cobblemon may project this data visually, but battle resolution reads
     * this server-owned snapshot rather than client/entity claims.
     */
    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant,
            Map<String, StatusSkipFeatureState> statusSkipFeaturesByCombatant
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
                requireKnownCombatant(combatantId, "status state");
                Set<String> normalized = normalizeStatuses(entry.getValue());
                if (!normalized.isEmpty()) {
                    this.statusesByCombatant.put(combatantId, normalized);
                }
            }
        }
        if (statusSkipFeaturesByCombatant != null) {
            for (Map.Entry<String, StatusSkipFeatureState> entry : statusSkipFeaturesByCombatant.entrySet()) {
                String combatantId = entry.getKey();
                requireKnownCombatant(combatantId, "status-skip feature state");
                StatusSkipFeatureState featureState = entry.getValue();
                if (featureState != null && !featureState.equals(StatusSkipFeatureState.NONE)) {
                    this.statusSkipFeaturesByCombatant.put(combatantId, featureState);
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

    public StatusSkipFeatureState statusSkipFeatures(String combatantId) {
        requireCombatant(combatantId);
        return statusSkipFeaturesByCombatant.getOrDefault(combatantId, StatusSkipFeatureState.NONE);
    }

    private void requireKnownCombatant(String combatantId, String source) {
        if (!this.combatants.containsKey(combatantId)) {
            throw new IllegalArgumentException(source + " references unknown combatant: " + combatantId);
        }
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
