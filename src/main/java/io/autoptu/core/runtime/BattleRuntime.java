package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.BattleEventFactory;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.PostDamageHookResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.Accuracy;
import io.autoptu.core.rules.AccuracyResult;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.DamageResolution;
import io.autoptu.core.rules.DamageResult;
import io.autoptu.core.rules.StatusSkipExceptionResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Stateful authoritative battle runtime for applying validated actions. */
public final class BattleRuntime {
    private BattleRuntime() {
    }

    public static AppliedActionResult applyAction(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput input
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, input, List.of(), PostDamageHookResult.empty()
        );
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput input
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, input, List.of(), PostDamageHookResult.empty()
        );
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookResult postDamageHooks
    ) {
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        if (postDamageHooks == null) throw new IllegalArgumentException("postDamageHooks is required");

        MoveChoiceRevalidation.requireLegalCombatantMove(state, choice, move, actorSize, targetSize, lineOfSightBlockers);

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        int roll = rng.randIntInclusive(1, 20);
        AccuracyResult accuracy = Accuracy.resolve(input.accuracyCheck(roll, null));
        if (!accuracy.hit() && input.rerollOnMiss()) {
            actor.consumeProbabilityControl();
            int reroll = rng.randIntInclusive(1, 20);
            accuracy = Accuracy.resolve(input.accuracyCheck(roll, reroll));
        }

        DamageResult damage = accuracy.hit() ? DamageResolution.resolve(rng, input.damageCheck(accuracy.crit())) : null;
        if (accuracy.hit()) {
            damage = applyPostDamageAdjustment(damage, postDamageHooks.flatDamageBonus());
        }
        AppliedActionResult result = applyResolvedMoveOutcome(state, choice, source, accuracy, damage);
        if (accuracy.hit()) {
            result = prependEvents(postDamageHooks.events(), result);
        }
        actor.moveFrequencyUsage().recordUse(move);
        return prependEvents(preResolutionEvents, result);
    }

    public static AppliedActionResult applyRevalidatedResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        MoveChoiceRevalidation.requireLegalCombatantMove(state, choice, move, actorSize, targetSize, lineOfSightBlockers);
        AppliedActionResult result = applyResolvedMoveOutcome(state, choice, source, accuracy, damage);
        state.requireCombatant(choice.actorId()).moveFrequencyUsage().recordUse(move);
        return result;
    }

    public static AppliedActionResult applyResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (accuracy == null) throw new IllegalArgumentException("accuracy is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("resolved move outcome currently requires a combatant target");
        }
        if (accuracy.hit() && damage == null) throw new IllegalArgumentException("damage is required for a hit");

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        ActionBudget budget = actor.actionBudget();
        ActionType actionType = choice.actionType();
        if (!budget.hasActionAvailable(actionType) && budget.extraCount(actionType) <= 0) {
            throw new IllegalStateException(actionType.value() + " action is already consumed");
        }

        int previousHp = target.hp();
        int resolvedDamage = accuracy.hit() ? Math.max(0, damage.damage()) : 0;
        int nextHp = Math.max(0, previousHp - resolvedDamage);
        if (!budget.consume(actionType, choice.moveId())) {
            throw new IllegalStateException(actionType.value() + " action is already consumed");
        }
        target.setHp(nextHp);
        if (accuracy.hit()) {
            int actualDamage = Math.max(0, previousHp - target.hp());
            state.damageHistory().recordMoveHit(actor.combatantId(), target.combatantId(), actualDamage);
        }

        MoveResolvedEvent event = BattleEventFactory.moveResolved(
                source, actor.combatantId(), target.combatantId(), choice.moveId(),
                accuracy, accuracy.hit() ? damage : null, target.hp()
        );
        return new AppliedActionResult(List.of(event));
    }

    public static AppliedActionResult applyStatusSkip(
            BattleRuntimeState state,
            String actorId,
            String status,
            String phase,
            String reason
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState actor = state.requireCombatant(actorId);
        StatusSkipExceptionResolution.Result exception = StatusSkipExceptionResolution.resolve(
                status,
                state.statusSkipFeatureState(actorId)
        );
        if (exception.ignored()) {
            return new AppliedActionResult(List.of(trainerFeatureEvent(actor, status, exception)));
        }

        StatusSkipResolution.Result skip = StatusSkipResolution.consume(actor.actionBudget());
        return new AppliedActionResult(List.of(new StatusSkipEvent(
                actorId, status, phase, reason, skip.standardConsumed(), skip.shiftConsumed(), actor.hp()
        )));
    }

    /** Clear round-scoped EOT usage when the outer turn controller advances the round. */
    public static void resetRoundMoveFrequency(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        for (String combatantId : state.combatantIds()) {
            state.requireCombatant(combatantId).moveFrequencyUsage().resetRound();
        }
    }

    private static DamageResult applyPostDamageAdjustment(DamageResult damage, int flatDamageAdjustment) {
        if (damage == null) throw new IllegalArgumentException("damage is required");
        if (flatDamageAdjustment == 0) return damage;
        int adjusted = Math.addExact(damage.damage(), flatDamageAdjustment);
        int finalDamage = Math.max(0, adjusted);
        return new DamageResult(
                damage.dice(),
                damage.baseRoll(),
                damage.criticalExtraRoll(),
                damage.damageRoll(),
                damage.preModifierDamage(),
                damage.preTypeDamage(),
                finalDamage
        );
    }

    private static AppliedActionResult prependEvents(
            List<? extends BattleEvent> before,
            AppliedActionResult result
    ) {
        if (before == null || before.isEmpty()) return result;
        ArrayList<BattleEvent> events = new ArrayList<>(before.size() + result.events().size());
        for (BattleEvent event : before) {
            if (event == null) throw new IllegalArgumentException("preResolutionEvents cannot contain null");
            events.add(event);
        }
        events.addAll(result.events());
        return new AppliedActionResult(events);
    }

    private static TrainerFeatureEvent trainerFeatureEvent(
            RuntimeCombatantState actor,
            String status,
            StatusSkipExceptionResolution.Result exception
    ) {
        return switch (exception.exceptionKind()) {
            case SUPREME_CONCENTRATION -> new TrainerFeatureEvent(
                    actor.combatantId(), "Signature Technique", "supreme_concentration",
                    exception.signatureMove(), status, actor.hp()
            );
            case DUELISTS_MANUAL -> new TrainerFeatureEvent(
                    actor.combatantId(), "Duelist's Manual", "ignore_status_skip",
                    "", status, actor.hp()
            );
            case NONE -> throw new IllegalStateException("ignored status skip requires a feature exception");
        };
    }
}
