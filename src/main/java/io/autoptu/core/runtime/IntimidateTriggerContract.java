package io.autoptu.core.runtime;

import io.autoptu.core.rules.Targeting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure server-owned contract for the pinned Python Intimidate trigger gate and adjacent-foe selection.
 *
 * <p>This deliberately stops before combat-stage mutation. It freezes the reusable lifecycle seam:
 * the holder must be active, conscious, own Intimidate, have joined in the current round, and not
 * already have used Intimidate in that round. Eligible foes are active, conscious opponents within
 * Chebyshev distance 1, preserving canonical battle insertion order.</p>
 */
public final class IntimidateTriggerContract {
    public static final String ABILITY = "Intimidate";
    public static final String JOINED_ROUND = "joined_round";
    public static final String USED = "intimidate_used";

    private IntimidateTriggerContract() {}

    public record Plan(boolean shouldTrigger, List<String> adjacentOpponentIds) {
        public Plan {
            adjacentOpponentIds = List.copyOf(adjacentOpponentIds == null ? List.of() : adjacentOpponentIds);
        }
    }

    public static Plan plan(BattleRuntimeState state, String holderId) {
        Objects.requireNonNull(state, "state");
        RuntimeCombatantState holder = state.requireCombatant(holderId);
        int round = state.currentRound();

        if (!state.isActive(holderId)
                || holder.hp() <= 0
                || !holder.hasAbilityExact(ABILITY)
                || !hasRoundEffect(holder, JOINED_ROUND, round)
                || hasRoundEffect(holder, USED, round)) {
            return new Plan(false, List.of());
        }

        ArrayList<String> targets = new ArrayList<>();
        String holderTeam = state.teamId(holderId);
        for (String candidateId : state.combatantIds()) {
            if (candidateId.equals(holderId)) continue;
            RuntimeCombatantState candidate = state.requireCombatant(candidateId);
            if (!state.isActive(candidateId) || candidate.hp() <= 0) continue;
            if (holderTeam.equals(state.teamId(candidateId))) continue;
            if (Targeting.chebyshevDistance(holder.position(), candidate.position()) > 1) continue;
            targets.add(candidateId);
        }
        return new Plan(true, targets);
    }

    private static boolean hasRoundEffect(RuntimeCombatantState combatant, String effectName, int round) {
        for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll(effectName)) {
            Object value = entry.payload().get("round");
            if (value instanceof Number number && number.intValue() == round) return true;
        }
        return false;
    }
}
