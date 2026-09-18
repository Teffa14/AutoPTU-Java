package io.autoptu.core.runtime;

import io.autoptu.core.rules.Targeting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable authoritative geometry view used by reaction discovery.
 *
 * <p>Adjacency is derived from PTU footprints in the core. Callers provide combatant snapshot
 * data only; they cannot inject preclassified adjacency booleans.</p>
 */
public final class AuthoritativeCombatGeometrySnapshot {
    private final Map<String, AuthoritativeCombatantGeometry> combatants;

    public AuthoritativeCombatGeometrySnapshot(List<AuthoritativeCombatantGeometry> combatants) {
        if (combatants == null) {
            throw new NullPointerException("combatants");
        }
        Map<String, AuthoritativeCombatantGeometry> indexed = new LinkedHashMap<>();
        for (AuthoritativeCombatantGeometry combatant : combatants) {
            if (combatant == null) {
                throw new NullPointerException("combatant");
            }
            if (indexed.putIfAbsent(combatant.combatantId(), combatant) != null) {
                throw new IllegalArgumentException("duplicate combatantId: " + combatant.combatantId());
            }
        }
        this.combatants = Map.copyOf(indexed);
    }

    public boolean adjacent(String firstCombatantId, String secondCombatantId) {
        AuthoritativeCombatantGeometry first = require(firstCombatantId);
        AuthoritativeCombatantGeometry second = require(secondCombatantId);
        return Targeting.footprintDistance(
                first.anchor(), first.sizeLabel(), second.anchor(), second.sizeLabel()) == 1;
    }

    public boolean anyAdjacentTo(String reactorId, List<String> targetIds) {
        if (targetIds == null) {
            throw new NullPointerException("targetIds");
        }
        for (String targetId : targetIds) {
            if (adjacent(reactorId, targetId)) {
                return true;
            }
        }
        return false;
    }

    private AuthoritativeCombatantGeometry require(String combatantId) {
        String normalized = combatantId == null ? "" : combatantId.strip();
        AuthoritativeCombatantGeometry combatant = combatants.get(normalized);
        if (combatant == null) {
            throw new IllegalArgumentException("unknown combatantId: " + normalized);
        }
        return combatant;
    }
}
