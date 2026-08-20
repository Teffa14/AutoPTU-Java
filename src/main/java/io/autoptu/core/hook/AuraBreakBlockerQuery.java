package io.autoptu.core.hook;

import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.BattleRuntimeState;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.ArrayList;
import java.util.List;

/**
 * Python BattleState._aura_break_blockers parity over authoritative battle state.
 *
 * Normal Aura Break is global in the pinned Python oracle: every active, non-fainted
 * enemy with the exact base ability is a blocker. Battle insertion order is preserved
 * because Python returns blockers in pokemon-dict iteration order and callers use the
 * first entry as the semantic event source.
 */
public final class AuraBreakBlockerQuery {
    private AuraBreakBlockerQuery() {
    }

    public static List<String> blockers(BattleRuntimeState state, String actorId) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState actor = state.requireCombatant(actorId);
        String actorTeam = state.teamId(actor.combatantId());
        ArrayList<String> blockers = new ArrayList<>();
        for (String candidateId : state.combatantIds()) {
            RuntimeCombatantState candidate = state.requireCombatant(candidateId);
            if (!state.isActive(candidateId) || candidate.hp() <= 0) continue;
            if (!AbilityIdentityResolution.matchesExact(candidate.abilities(), "Aura Break")) continue;
            String candidateTeam = state.teamId(candidateId);
            if (candidateTeam == null || candidateTeam.isBlank()) continue;
            if (candidateTeam.equals(actorTeam)) continue;
            blockers.add(candidateId);
        }
        return List.copyOf(blockers);
    }
}