package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.BattleEventFactory;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.PostDamageHookContext;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PostDamageHookResult;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.ShiftApplicationResult;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.Accuracy;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.DamageResolution;
import io.autoptu.core.rules.Movement;
import io.autoptu.core.rules.ShiftApplication;
import io.autoptu.core.rules.StatusSkipExceptionResolution;
import io.autoptu.core.rules.StatusSkipResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class BattleRuntime {
    private BattleRuntime() {}

    public static AppliedActionResult applyAction(BattleRuntimeState state, BattleChoice choice, Predicate<GridCoord> canFit) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        if (choice instanceof ShiftChoice shiftChoice) return applyShift(state, actor, shiftChoice, canFit);
        if (choice instanceof MoveChoice) throw new UnsupportedOperationException("MoveChoice requires authoritative move context; use applyAuthoritativeMove");
        throw new IllegalArgumentException("unsupported battle choice: " + choice.getClass().getName());
    }

    public static AppliedActionResult applyStatusSkip(
            BattleRuntimeState state,
            String actorId,
            String status,
            TurnPhase phase,
            String reason
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (phase == null) throw new IllegalArgumentException("phase is required");

        RuntimeCombatantState actor = state.requireCombatant(actorId);
        StatusSkipFeatureState featureState = state.statusSkipFeatures(actorId);
        StatusSkipExceptionResolution.Result exception = StatusSkipExceptionResolution.resolve(
                status,
                featureState.signatureModification(),
                featureState.signatureMove(),
                featureState.duelistsManualIgnoreStatus()
        );
        if (!exception.skipTurn()) {
            TrainerFeatureEvent event = trainerFeatureEvent(actor, status, exception);
            return new AppliedActionResult(List.of(event));
        }

        StatusSkipResolution.apply(actor.actionBudget());
        StatusSkipEvent event = new StatusSkipEvent(actor.combatantId(), status, phase, reason);
        return new AppliedActionResult(List.of(event));
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, input, List.of(), PostDamageHookResult.empty()
        );
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, input, preResolutionEvents, PostDamageHookResult.empty()
        );
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookResult postDamageHooks
    ) {
        if (postDamageHooks == null) throw new IllegalArgumentException("postDamageHooks is required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, postDamageHooks, null, null,
                MoveExecutionPolicy.ORDINARY
        );
    }

    /**
     * Preferred move path for stateful post-result effects. The registry executes only after
     * accuracy and ordinary DamageResolution have consumed the authoritative RNG. This matches
     * Python post_result hook timing while keeping HP/history mutation downstream of the final
     * signed damage adjustment.
     */
    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");
        if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, null, postDamageHookRegistry, effectiveMetadata,
                MoveExecutionPolicy.ORDINARY
        );
    }

    /**
     * Mature delayed-hit path. The move re-enters the same accuracy/damage/post-result pipeline
     * as an ordinary move, but its action and move-frequency resources were already paid when
     * the delayed effect was scheduled. Current target/range/footprint/LoS state is still
     * revalidated server-side.
     */
    public static AppliedActionResult applyDelayedAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");
        if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, null, postDamageHookRegistry, effectiveMetadata,
                MoveExecutionPolicy.DELAYED_TRIGGER
        );
    }

    private static AppliedActionResult applyAuthoritativeMoveInternal(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookResult precomputedPostDamageHooks,
            PostDamageHookRegistry deferredPostDamageHooks,
            MoveCombatProfile effectiveMetadata,
            MoveExecutionPolicy executionPolicy
    ) {
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        if (executionPolicy == null) throw new IllegalArgumentException("executionPolicy is required");

        if (executionPolicy.validateOrdinaryLegality()) {
            MoveChoiceRevalidation.requireLegalCombatantMove(
                    state, choice, move, actorSize, targetSize, lineOfSightBlockers
            );
        } else {
            MoveChoiceRevalidation.requireLegalDelayedCombatantMove(
                    state, choice, move, actorSize, targetSize, lineOfSightBlockers
            );
        }

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        int roll = rng.randIntInclusive(1, 20);
        AccuracyResult accuracy = Accuracy.resolve(input.accuracyCheck(roll, null));
        if (!accuracy.hit() && input.rerollOnMiss()) {
            actor.consumeProbabilityControl();
            int reroll = rng.randIntInclusive(1, 20);
            accuracy = Accuracy.resolve(input.accuracyCheck(roll, reroll));
        }

        DamageResult damage = accuracy.hit() ? DamageResolution.resolve(rng, input.damageCheck(accuracy.crit())) : null;
        PostDamageHookResult resolvedPostDamageHooks = precomputedPostDamageHooks == null
                ? PostDamageHookResult.empty()
                : precomputedPostDamageHooks;
        if (accuracy.hit() && deferredPostDamageHooks != null) {
            resolvedPostDamageHooks = deferredPostDamageHooks.resolve(new PostDamageHookContext(
                    state,
                    choice.actorId(),
                    choice.targetId(),
                    actor,
                    target,
                    move,
                    effectiveMetadata,
                    rng
            ));
        }
        if (accuracy.hit()) {
            damage = applyPostDamageAdjustment(damage, resolvedPostDamageHooks.flatDamageBonus());
        }
        AppliedActionResult result = applyResolvedMoveOutcome(
                state, choice, source, accuracy, damage, executionPolicy
        );
        if (accuracy.hit()) {
            result = prependEvents(resolvedPostDamageHooks.events(), result);
        }
        if (executionPolicy.recordFrequencyUse()) {
            actor.moveFrequencyUsage().recordUse(move);
        }
        return prependEvents(preResolutionEvents, result);
    }

    public static AppliedActionResult applyRevalidatedResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        MoveChoiceRevalidation.requireLegalCombatantMove(state, choice, move, actorSize, targetSize, lineOfSightBlockers);
        AppliedActionResult result = applyResolvedMoveOutcome(
                state, choice, source, accuracy, damage, MoveExecutionPolicy.ORDINARY
        );
        state.requireCombatant(choice.actorId()).moveFrequencyUsage().recordUse(move);
        return result;
    }

    public static AppliedActionResult applyResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        return applyResolvedMoveOutcome(
                state, choice, source, accuracy, damage, MoveExecutionPolicy.ORDINARY
        );
    }

    private static AppliedActionResult applyResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, String source,
            AccuracyResult accuracy, DamageResult damage,
            MoveExecutionPolicy executionPolicy
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (accuracy == null) throw new IllegalArgumentException("accuracy is required");
        if (executionPolicy == null) throw new IllegalArgumentException("executionPolicy is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("resolved move outcome currently requires a combatant target");
        }
        if (accuracy.hit() && damage == null) throw new IllegalArgumentException("damage is required for a hit");

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        ActionBudget budget = actor.actionBudget();
        ActionType actionType = choice.actionType();
        if (executionPolicy.consumeAction()
                && !budget.hasActionAvailable(actionType)
                && budget.extraCount(actionType) <= 0) {
            throw new IllegalStateException(actionType.value() + " action is already consumed");
        }

        int previousHp = target.hp();
        int resolvedDamage = accuracy.hit() ? Math.max(0, damage.damage()) : 0;
        int nextHp = Math.max(0, previousHp - resolvedDamage);
        if (executionPolicy.consumeAction() && !budget.consume(actionType, choice.moveId())) {
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

    public static void resetRoundMoveFrequency(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        for (String combatantId : state.combatantIds()) {
            state.requireCombatant(combatantId).moveFrequencyUsage().resetRound();
        }
    }

    private static DamageResult applyPostDamageAdjustment(DamageResult damage, int flatDamageAdjustment) {
        if (damage == null) throw new IllegalArgumentException("damage is required");
        if (flatDamageAdjustment == 0) return damage;
        int finalDamage = Math.max(0, Math.addExact(damage.damage(), flatDamageAdjustment));
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
            case NONE -> throw new IllegalStateException("status skip exception expected");
        };
    }

    private static AppliedActionResult applyShift(
            BattleRuntimeState state, RuntimeCombatantState actor,
            ShiftChoice choice, Predicate<GridCoord> canFit
    ) {
        Set<GridCoord> legalDestinations = Movement.legalShiftTiles(state.grid(), actor.movementProfile(), 0, canFit);
        ShiftApplicationResult result = ShiftApplication.apply(
                actor.combatantId(), actor.position(), choice.destination(),
                legalDestinations, actor.actionBudget()
        );
        actor.moveTo(result.position());
        return new AppliedActionResult(List.of(result.event()));
    }
}
