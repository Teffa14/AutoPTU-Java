package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.LifecycleHookRegistry;
import io.autoptu.core.hook.SwitchTriggerDecisionPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Server-authoritative materializer for an already accepted switch-trigger decision.
 *
 * <p>The planner owns eligibility-time policy. This executor revalidates the chosen replacement
 * against live battle state, preflights the switch before spending AP, and then applies the
 * parity-sensitive side effects in the order frozen from the pinned Python oracle: spend AP,
 * execute the canonical switch transaction, add the sent-out temporary effect, then emit the
 * semantic Trainer Feature event. Adapters may supply the user's accepted replacement choice but
 * never mutate AP, activity, temporary effects, or switch state themselves.</p>
 */
public final class SwitchTriggerDecisionExecutor {
    private SwitchTriggerDecisionExecutor() {
    }

    public static ExecutionResult execute(
            BattleRuntimeState state,
            CombatantFieldPresenceStore fieldPresence,
            LifecycleHookRegistry lifecycleHooks,
            Consumer<BattleEvent> eventSink,
            SwitchTriggerDecisionPlan plan,
            String selectedReplacementId
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (fieldPresence == null) throw new IllegalArgumentException("field presence store is required");
        if (lifecycleHooks == null) throw new IllegalArgumentException("lifecycle hook registry is required");
        if (eventSink == null) throw new IllegalArgumentException("event sink is required");
        if (plan == null) throw new IllegalArgumentException("switch trigger plan is required");
        if (selectedReplacementId == null || selectedReplacementId.isBlank()) {
            throw new IllegalArgumentException("selected replacement is required");
        }

        String replacementId = selectedReplacementId.strip();
        if (!plan.replacementIds().contains(replacementId)) {
            throw new IllegalArgumentException("selected replacement is not a planned candidate: " + replacementId);
        }

        RuntimeCombatantState actor = state.requireCombatant(plan.actorId());
        RuntimeCombatantState replacement = state.requireCombatant(replacementId);
        if (!state.hasCanonicalTrainer(plan.actorId()) || !state.hasCanonicalTrainer(replacementId)) {
            throw new IllegalArgumentException("switch trigger combatants require canonical trainer/controller state");
        }
        if (!state.controllerId(plan.actorId()).equals(state.controllerId(replacementId))) {
            throw new IllegalArgumentException("selected replacement must share the actor controller");
        }
        if (!state.isActive(plan.actorId()) || actor.hp() <= 0) {
            throw new IllegalArgumentException("switch trigger actor must still be active and non-fainted");
        }
        if (state.isActive(replacementId) || replacement.hp() <= 0) {
            throw new IllegalArgumentException("selected replacement must still be benched and non-fainted");
        }

        TrainerRuntimeState trainer = state.requireTrainerForCombatant(plan.actorId());
        if (!trainer.hasTrainerFeature(plan.featureName())) {
            throw new IllegalArgumentException("trainer no longer owns planned feature: " + plan.featureName());
        }
        if (trainer.ap() < plan.requiredAp() || trainer.ap() < plan.consumeAp()) {
            throw new IllegalArgumentException("trainer no longer has enough AP for switch trigger");
        }

        // Resolve and validate every canonical switch precondition before the Python-ordered AP spend.
        CombatantSwitchExecutionPlan switchPlan = CombatantSwitchExecutionPlan.resolve(
                state,
                plan.actorId(),
                replacementId
        );
        CombatantSwitchTransitionPlan transition = switchPlan.transition();
        if (!fieldPresence.isOnField(transition.outgoingId())) {
            throw new IllegalArgumentException("outgoing combatant must be on-field");
        }
        if (fieldPresence.isOnField(transition.replacementId())) {
            throw new IllegalArgumentException("replacement combatant must be off-field");
        }
        if (!fieldPresence.position(transition.outgoingId())
                .orElseThrow(() -> new IllegalArgumentException("outgoing field position is required"))
                .equals(transition.replacementDestination())) {
            throw new IllegalArgumentException("field presence disagrees with outgoing switch destination");
        }

        ArrayList<Stage> stages = new ArrayList<>();
        int apBefore = trainer.ap();
        if (plan.consumeAp() > 0) {
            if (!trainer.spendAp(plan.consumeAp())) {
                throw new IllegalStateException("trainer AP changed after switch trigger preflight");
            }
            stages.add(Stage.SPEND_AP);
        }

        CombatantSwitchExecutor.ExecutionResult switchResult = CombatantSwitchExecutor.execute(
                state,
                fieldPresence,
                lifecycleHooks,
                eventSink,
                plan.actorId(),
                replacementId,
                plan.switchPolicy().allowReplacementTurn(),
                plan.switchPolicy().allowImmediate()
        );
        stages.add(Stage.APPLY_SWITCH);

        if (!plan.sentOutEffectKey().isBlank()) {
            int round = state.currentRound();
            replacement.temporaryEffects().add(
                    plan.sentOutEffectKey(),
                    Map.of("round", round, "expires_round", round)
            );
            stages.add(Stage.ADD_SENT_OUT_EFFECT);
        }

        TrainerFeatureEvent featureEvent = new TrainerFeatureEvent(
                plan.actorId(),
                plan.featureName(),
                "switch",
                Map.of(
                        "trigger", plan.trigger().oracleName(),
                        "ap_cost", plan.consumeAp()
                )
        );
        eventSink.accept(featureEvent);
        stages.add(Stage.EMIT_TRAINER_FEATURE_EVENT);

        return new ExecutionResult(
                plan,
                replacementId,
                apBefore,
                trainer.ap(),
                switchResult,
                featureEvent,
                List.copyOf(stages)
        );
    }

    public enum Stage {
        SPEND_AP,
        APPLY_SWITCH,
        ADD_SENT_OUT_EFFECT,
        EMIT_TRAINER_FEATURE_EVENT
    }

    public record ExecutionResult(
            SwitchTriggerDecisionPlan plan,
            String replacementId,
            int apBefore,
            int apAfter,
            CombatantSwitchExecutor.ExecutionResult switchResult,
            TrainerFeatureEvent featureEvent,
            List<Stage> stages
    ) {
        public ExecutionResult {
            if (plan == null) throw new IllegalArgumentException("switch trigger plan is required");
            if (replacementId == null || replacementId.isBlank()) {
                throw new IllegalArgumentException("replacementId is required");
            }
            if (apBefore < 0 || apAfter < 0) throw new IllegalArgumentException("AP cannot be negative");
            if (switchResult == null) throw new IllegalArgumentException("switch result is required");
            if (featureEvent == null) throw new IllegalArgumentException("feature event is required");
            stages = stages == null ? List.of() : List.copyOf(stages);
        }
    }
}
