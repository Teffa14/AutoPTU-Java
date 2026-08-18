package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.MovementGrid;

import java.util.ArrayList;
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
    private final LinkedHashMap<String, CombatantGeometryState> geometryByCombatant = new LinkedHashMap<>();
    private final LinkedHashMap<String, CombatantAffiliationState> affiliationByCombatant = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<MoveOption>> movesByCombatant = new LinkedHashMap<>();

    public BattleRuntimeState(MovementGrid grid, List<RuntimeCombatantState> combatants) {
        this(grid, combatants, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant
    ) {
        this(grid, combatants, statusesByCombatant, Map.of(), Map.of(), Map.of(), Map.of());
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
        this(grid, combatants, statusesByCombatant, statusSkipFeaturesByCombatant, Map.of(), Map.of(), Map.of());
    }

    /**
     * Full authoritative battle snapshot for the geometry-aware runtime slices.
     *
     * Geometry is intentionally separate from Minecraft/Cobblemon entity scale. PTU
     * targeting and footprints read this map so a visual model, packet, or adapter
     * cannot change melee reach by claiming a different size.
     */
    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant,
            Map<String, StatusSkipFeatureState> statusSkipFeaturesByCombatant,
            Map<String, CombatantGeometryState> geometryByCombatant
    ) {
        this(grid, combatants, statusesByCombatant, statusSkipFeaturesByCombatant, geometryByCombatant, Map.of(), Map.of());
    }

    /**
     * Full authoritative battle snapshot for current runtime slices.
     *
     * Affiliation is battle state, not a Minecraft scoreboard/team claim. A missing
     * affiliation intentionally falls back to a one-combatant team so legacy fixtures
     * preserve the old behavior where every other combatant was a possible opponent.
     */
    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant,
            Map<String, StatusSkipFeatureState> statusSkipFeaturesByCombatant,
            Map<String, CombatantGeometryState> geometryByCombatant,
            Map<String, CombatantAffiliationState> affiliationByCombatant
    ) {
        this(
                grid,
                combatants,
                statusesByCombatant,
                statusSkipFeaturesByCombatant,
                geometryByCombatant,
                affiliationByCombatant,
                Map.of()
        );
    }

    /**
     * Full authoritative battle snapshot including combatant movesets.
     *
     * Move ownership belongs to the battle snapshot. Minecraft/Cobblemon adapters may
     * display move buttons or AI options, but they cannot grant a combatant a move that
     * was not materialized here from canonical PTU state.
     */
    public BattleRuntimeState(
            MovementGrid grid,
            List<RuntimeCombatantState> combatants,
            Map<String, ? extends Collection<String>> statusesByCombatant,
            Map<String, StatusSkipFeatureState> statusSkipFeaturesByCombatant,
            Map<String, CombatantGeometryState> geometryByCombatant,
            Map<String, CombatantAffiliationState> affiliationByCombatant,
            Map<String, ? extends Collection<MoveOption>> movesByCombatant
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
        if (geometryByCombatant != null) {
            for (Map.Entry<String, CombatantGeometryState> entry : geometryByCombatant.entrySet()) {
                String combatantId = entry.getKey();
                requireKnownCombatant(combatantId, "combatant geometry");
                CombatantGeometryState geometry = entry.getValue();
                if (geometry != null && !geometry.equals(CombatantGeometryState.MEDIUM)) {
                    this.geometryByCombatant.put(combatantId, geometry);
                }
            }
        }
        if (affiliationByCombatant != null) {
            for (Map.Entry<String, CombatantAffiliationState> entry : affiliationByCombatant.entrySet()) {
                String combatantId = entry.getKey();
                requireKnownCombatant(combatantId, "combatant affiliation");
                CombatantAffiliationState affiliation = entry.getValue();
                if (affiliation != null) {
                    this.affiliationByCombatant.put(combatantId, affiliation);
                }
            }
        }
        if (movesByCombatant != null) {
            for (Map.Entry<String, ? extends Collection<MoveOption>> entry : movesByCombatant.entrySet()) {
                String combatantId = entry.getKey();
                requireKnownCombatant(combatantId, "combatant moveset");
                this.movesByCombatant.put(combatantId, copyMoveOptions(entry.getValue()));
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

    /** Stable battle insertion order used by deterministic action-space generation. */
    public List<String> combatantIds() {
        return List.copyOf(combatants.keySet());
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

    public CombatantGeometryState geometry(String combatantId) {
        requireCombatant(combatantId);
        return geometryByCombatant.getOrDefault(combatantId, CombatantGeometryState.MEDIUM);
    }

    public CombatantAffiliationState affiliation(String combatantId) {
        requireCombatant(combatantId);
        return affiliationByCombatant.getOrDefault(
                combatantId,
                CombatantAffiliationState.active(combatantId)
        );
    }

    public String teamId(String combatantId) {
        return affiliation(combatantId).teamId();
    }

    public boolean isActive(String combatantId) {
        return affiliation(combatantId).active();
    }

    /** True when this snapshot explicitly owns the combatant's moveset, including an empty moveset. */
    public boolean hasCanonicalMoves(String combatantId) {
        requireCombatant(combatantId);
        return movesByCombatant.containsKey(combatantId);
    }

    /** Canonical move definitions for the combatant, defensively copied at snapshot construction. */
    public List<MoveOption> moveOptions(String combatantId) {
        requireCombatant(combatantId);
        return movesByCombatant.getOrDefault(combatantId, List.of());
    }

    /** Python AI candidate semantics: active, non-fainted combatants only. */
    public boolean isTargetableCombatant(String combatantId) {
        RuntimeCombatantState combatant = requireCombatant(combatantId);
        return isActive(combatantId) && combatant.hp() > 0;
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

    private static List<MoveOption> copyMoveOptions(Collection<MoveOption> moves) {
        if (moves == null || moves.isEmpty()) {
            return List.of();
        }
        ArrayList<MoveOption> copied = new ArrayList<>();
        LinkedHashSet<String> moveIds = new LinkedHashSet<>();
        for (MoveOption move : moves) {
            if (move == null) {
                continue;
            }
            if (!moveIds.add(move.moveId())) {
                throw new IllegalArgumentException("duplicate moveId in combatant moveset: " + move.moveId());
            }
            copied.add(move);
        }
        return List.copyOf(copied);
    }
}
