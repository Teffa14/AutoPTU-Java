package io.autoptu.core.runtime;

import io.autoptu.core.hook.SwitchTriggerDecisionPlan;
import io.autoptu.core.hook.SwitchTriggerPlannerRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects server-authoritative battle state into the generic switch-trigger planner contract.
 *
 * <p>This boundary intentionally derives Trainer Feature ownership, AP, active/fainted state,
 * replacement legality, and trigger-target temporary-effect guards inside the core. Minecraft,
 * Cobblemon, and Craftics may render the resulting decision, but must not reconstruct these PTU
 * inputs independently.</p>
 */
public final class RuntimeSwitchTriggerPlanningContextFactory {
    private RuntimeSwitchTriggerPlanningContextFactory() {
    }

    public static SwitchTriggerPlannerRegistry.PlanningContext fromState(
            BattleRuntimeState state,
            SwitchTriggerDecisionPlan.Trigger trigger,
            String actorId
    ) {
        return fromState(state, trigger, actorId, null, "");
    }

    /**
     * Build one deterministic planner context from canonical runtime state.
     *
     * @param triggerTargetId combatant whose temporary effects guard this trigger, when applicable
     * @param handledEffectKey normalized temporary-effect family that marks the trigger as handled
     */
    public static SwitchTriggerPlannerRegistry.PlanningContext fromState(
            BattleRuntimeState state,
            SwitchTriggerDecisionPlan.Trigger trigger,
            String actorId,
            String triggerTargetId,
            String handledEffectKey
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (trigger == null) throw new IllegalArgumentException("trigger is required");
        RuntimeCombatantState actor = state.requireCombatant(actorId);
        TrainerRuntimeState trainer = state.requireTrainerForCombatant(actorId);
        String trainerId = state.controllerId(actorId);

        ArrayList<String> replacementIds = new ArrayList<>();
        for (String candidateId : state.combatantIds()) {
            RuntimeCombatantState candidate = state.requireCombatant(candidateId);
            if (!state.hasCanonicalTrainer(candidateId)) continue;
            if (!trainerId.equals(state.controllerId(candidateId))) continue;
            if (state.isActive(candidateId)) continue;
            if (candidate.hp() <= 0) continue;
            replacementIds.add(candidateId);
        }

        boolean triggerAlreadyHandled = false;
        if (triggerTargetId != null && !triggerTargetId.isBlank()
                && handledEffectKey != null && !handledEffectKey.isBlank()) {
            RuntimeCombatantState triggerTarget = state.requireCombatant(triggerTargetId);
            triggerAlreadyHandled = triggerTarget.temporaryEffects().has(handledEffectKey);
        }

        return new SwitchTriggerPlannerRegistry.PlanningContext(
                trigger,
                actor.combatantId(),
                state.isActive(actorId),
                actor.hp() <= 0,
                trainer.ap(),
                List.copyOf(replacementIds),
                java.util.Set.copyOf(trainer.trainerFeatures()),
                triggerAlreadyHandled
        );
    }
}
