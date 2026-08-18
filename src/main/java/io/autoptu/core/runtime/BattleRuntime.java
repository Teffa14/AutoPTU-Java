package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.BattleEventFactory;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
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

    /**
     * Resolve the Python StatusController pending status-skip path from canonical state.
     *
     * Trainer Feature bypasses are derived server-side before action buckets are consumed.
     * Minecraft/Cobblemon receives only semantic playback events and cannot decide that a
     * status skip is ignored.
     */
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
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (input == null) throw new IllegalArgumentException("input is required");

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
        return applyResolvedMoveOutcome(state, choice, source, accuracy, damage);
    }

    public static AppliedActionResult applyRevalidatedResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        MoveChoiceRevalidation.requireLegalCombatantMove(state, choice, move, actorSize, targetSize, lineOfSightBlockers);
        return applyResolvedMoveOutcome(state, choice, source, accuracy, damage);
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

        int resolvedDamage = accuracy.hit() ? Math.max(0, damage.damage()) : 0;
        int nextHp = Math.max(0, target.hp() - resolvedDamage);
        if (!budget.consume(actionType, choice.moveId())) {
            throw new IllegalStateException(actionType.value() + " action is already consumed");
        }
        target.setHp(nextHp);

        MoveResolvedEvent event = BattleEventFactory.moveResolved(
                source, actor.combatantId(), target.combatantId(), choice.moveId(),
                accuracy, accuracy.hit() ? damage : null, target.hp()
        );
        return new AppliedActionResult(List.of(event));
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
