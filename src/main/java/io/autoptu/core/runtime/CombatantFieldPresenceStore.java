package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Authoritative field-presence store for combatants that currently occupy the battle grid.
 *
 * <p>Absence from this store means off-field. This keeps recalled/replaced combatants from
 * requiring nullable {@code MovementProfile} positions while preserving Python's
 * {@code position=None} semantics at the battle boundary.</p>
 */
public final class CombatantFieldPresenceStore {
    private final LinkedHashMap<String, GridCoord> positionsByCombatant = new LinkedHashMap<>();

    public CombatantFieldPresenceStore(Map<String, GridCoord> initialPositions) {
        if (initialPositions == null) return;
        for (Map.Entry<String, GridCoord> entry : initialPositions.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public boolean isOnField(String combatantId) {
        requireId(combatantId);
        return positionsByCombatant.containsKey(combatantId);
    }

    public Optional<GridCoord> position(String combatantId) {
        requireId(combatantId);
        return Optional.ofNullable(positionsByCombatant.get(combatantId));
    }

    public Map<String, GridCoord> snapshot() {
        return Map.copyOf(positionsByCombatant);
    }

    /** Runtime-only boundary for the Python outgoing.position = None switch stage. */
    void removeFromRuntime(String combatantId) {
        requireId(combatantId);
        positionsByCombatant.remove(combatantId);
    }

    /** Runtime-only boundary for the Python replacement.position = outgoing_position switch stage. */
    void placeFromRuntime(String combatantId, GridCoord position) {
        put(combatantId, position);
    }

    /** Runtime-only switch materialization boundary retained for plan-level callers. */
    void applySwitchTransitionFromRuntime(CombatantSwitchTransitionPlan plan) {
        if (plan == null) throw new IllegalArgumentException("switch transition plan is required");
        if (!plan.outgoingOffFieldAfter()) {
            throw new IllegalArgumentException("switch plan must move outgoing combatant off-field");
        }
        if (!plan.replacementActiveAfter()) {
            throw new IllegalArgumentException("switch plan must activate replacement combatant");
        }
        if (plan.replacementDestination() == null) {
            throw new IllegalArgumentException("replacement destination is required");
        }

        removeFromRuntime(plan.outgoingId());
        placeFromRuntime(plan.replacementId(), plan.replacementDestination());
    }

    private void put(String combatantId, GridCoord position) {
        requireId(combatantId);
        if (position == null) throw new IllegalArgumentException("field position is required");
        positionsByCombatant.put(combatantId, position);
    }

    private static void requireId(String combatantId) {
        if (combatantId == null || combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
    }
}
