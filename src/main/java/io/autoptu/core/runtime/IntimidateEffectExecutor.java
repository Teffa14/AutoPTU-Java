package io.autoptu.core.runtime;

import io.autoptu.core.event.AbilityEvent;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.CombatStageChangedEvent;
import io.autoptu.core.model.CombatStageStat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-authoritative execution seam for the pinned Python Intimidate effect.
 *
 * <p>Target selection remains in {@link IntimidateTriggerContract}. This executor owns
 * the once-per-round marker and routes Attack CS mutation through the generic combat-stage
 * prevention/reaction pipeline. Content adapters never mutate stages directly.</p>
 */
public final class IntimidateEffectExecutor {
    private IntimidateEffectExecutor() {}

    public static List<BattleEvent> apply(
            BattleRuntimeState state,
            String holderId,
            IntimidateTriggerContract.Plan plan
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(plan, "plan");
        RuntimeCombatantState holder = state.requireCombatant(holderId);
        if (!plan.shouldTrigger()) return List.of();

        int round = state.currentRound();
        holder.temporaryEffects().add(IntimidateTriggerContract.USED, Map.of("round", round));

        CombatStageMutationService stages = CombatStageMutationService.authoritative(state);
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (String targetId : plan.adjacentOpponentIds()) {
            RuntimeCombatantState target = state.requireCombatant(targetId);
            CombatStageMutationResult mutation = stages.apply(
                    holderId,
                    targetId,
                    IntimidateTriggerContract.ABILITY,
                    CombatStageStat.ATK,
                    -1,
                    "intimidate"
            );

            if (mutation.baseAppliedDelta() != 0) {
                events.add(new CombatStageChangedEvent(
                        holderId,
                        targetId,
                        IntimidateTriggerContract.ABILITY,
                        CombatStageStat.ATK,
                        "intimidate",
                        Math.abs(mutation.baseAppliedDelta()),
                        mutation.baseStage(),
                        "Intimidate lowers Attack by -1 CS.",
                        target.hp(),
                        round,
                        "start"
                ));
                // Python records the committed base mutation before POST_APPLY reaction traces.
                events.addAll(mutation.events());
            } else {
                // PRE_APPLY blockers/reflections own the complete trace when no base mutation commits.
                events.addAll(mutation.events());
            }
            events.add(new AbilityEvent(
                    holderId,
                    IntimidateTriggerContract.ABILITY,
                    "attack_drop",
                    Map.of(
                            "target", targetId,
                            "move", IntimidateTriggerContract.ABILITY,
                            "targetHp", target.hp(),
                            "round", round,
                            "phase", "start"
                    )
            ));
        }
        return List.copyOf(events);
    }
}
