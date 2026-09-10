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

    /** Runtime-only switch materialization boundary. */
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

        positionsByCombatant.remove(plan.outgoingId());
        positionsByCombatant.put(plan.replacementId(), plan.replacementDestination());
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
