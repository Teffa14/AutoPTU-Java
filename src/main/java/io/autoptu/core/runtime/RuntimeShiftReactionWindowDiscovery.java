package io.autoptu.core.runtime;

import io.autoptu.core.hook.ShiftReactionWindowDiscovery;
import io.autoptu.core.model.GridCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime bridge from canonical battle state to the pure Shift reaction-window geometry.
 *
 * <p>The battle snapshot owns affiliation, active state, footprint size, position, and
 * deterministic combatant insertion order. This bridge only selects potential opposing
 * reactors from that state. Feature/status/resource eligibility remains a later reaction
 * resolver concern.</p>
 */
public final class RuntimeShiftReactionWindowDiscovery {
    private RuntimeShiftReactionWindowDiscovery() {}

    public static List<ShiftReactionWindowDiscovery.DiscoveredWindow> discover(
            BattleRuntimeState state,
            String shiftedCombatantId,
            GridCoord shiftedBeforeAnchor,
            GridCoord shiftedAfterAnchor,
            String triggerKey
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState shifted = state.requireCombatant(shiftedCombatantId);
        if (shiftedBeforeAnchor == null) throw new IllegalArgumentException("shiftedBeforeAnchor is required");
        if (shiftedAfterAnchor == null) throw new IllegalArgumentException("shiftedAfterAnchor is required");
        if (triggerKey == null || triggerKey.isBlank()) throw new IllegalArgumentException("triggerKey is required");

        String shiftedTeam = state.teamId(shiftedCombatantId);
        List<ShiftReactionWindowDiscovery.Reactor> reactors = new ArrayList<>();
        for (String combatantId : state.combatantIds()) {
            if (combatantId.equals(shiftedCombatantId)) continue;
            if (!state.isTargetableCombatant(combatantId)) continue;
            if (state.teamId(combatantId).equals(shiftedTeam)) continue;

            RuntimeCombatantState reactor = state.requireCombatant(combatantId);
            reactors.add(new ShiftReactionWindowDiscovery.Reactor(
                    combatantId,
                    reactor.position(),
                    state.geometry(combatantId).sizeLabel()
            ));
        }

        return ShiftReactionWindowDiscovery.discover(
                reactors,
                shifted.combatantId(),
                shiftedBeforeAnchor,
                shiftedAfterAnchor,
                state.geometry(shiftedCombatantId).sizeLabel(),
                triggerKey
        );
    }
}
