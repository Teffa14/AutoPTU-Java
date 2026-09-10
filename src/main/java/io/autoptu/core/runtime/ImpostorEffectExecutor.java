package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.random.PythonRandom;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authoritative execution seam for the pinned Python Impostor trigger.
 *
 * <p>The executor composes the generic nearest-opponent resolver, battle-owned
 * Python-compatible RNG, and shared transformation state mutation. It owns only
 * Impostor-specific lifecycle guards, the once-per-round marker, and semantic event.</p>
 */
public final class ImpostorEffectExecutor {
    public static final String ABILITY = "Impostor";
    public static final String JOINED_ROUND = "joined_round";
    public static final String USED = "impostor_used";

    private ImpostorEffectExecutor() {
    }

    public static List<BattleEvent> apply(BattleRuntimeState state, String holderId) {
        Objects.requireNonNull(state, "state");
        RuntimeCombatantState holder = state.requireCombatant(holderId);
        int round = state.currentRound();

        if (!state.isActive(holderId)
                || holder.hp() <= 0
                || !EffectiveAbilityResolver.hasExact(holder, ABILITY)
                || !hasRoundEffect(holder, JOINED_ROUND, round)
                || hasRoundEffect(holder, USED, round)) {
            return List.of();
        }

        NearestActiveOpponentResolver.Selection match = NearestActiveOpponentResolver.resolve(
                state,
                holderId,
                state.combatantIds()
        ).orElse(null);
        if (match == null) return List.of();

        RuntimeCombatantState target = state.requireCombatant(match.targetId());
        List<String> targetAbilities = EffectiveAbilityResolver.resolve(target);
        String copiedAbility = null;
        if (!targetAbilities.isEmpty()) {
            PythonRandom random = state.delayedHitStateFromRuntime().randomFromRuntime();
            copiedAbility = targetAbilities.get(random.choiceIndex(targetAbilities.size()));
        }

        TransformationStateResolver.Result result = TransformationStateResolver.apply(
                holder,
                target,
                copiedAbility,
                ABILITY
        );
        holder.temporaryEffects().add(USED, Map.of("round", round));

        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("target", match.targetId());
        details.put("copied_stages", result.copiedStages());
        details.put("ability_assigned", result.abilityAssigned());
        details.put("round", round);
        details.put("phase", "start");

        return List.of(new AbilityEvent(
                holderId,
                ABILITY,
                "transform",
                details
        ));
    }

    private static boolean hasRoundEffect(RuntimeCombatantState combatant, String effectName, int round) {
        for (TemporaryEffectEntry entry : combatant.temporaryEffects().getAll(effectName)) {
            Object value = entry.payload().get("round");
            if (value instanceof Number number && number.intValue() == round) return true;
        }
        return false;
    }
}
