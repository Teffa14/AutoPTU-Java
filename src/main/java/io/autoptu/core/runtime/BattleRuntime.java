package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.BattleEventFactory;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.ShiftApplicationResult;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.Movement;
import io.autoptu.core.rules.ShiftApplication;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * First authoritative action dispatcher for headless and Minecraft execution.
 *
 * The runtime revalidates choices from core state before mutation. Controllers may
 * select a BattleChoice, but they cannot directly set positions, HP, or spend actions.
 */
public final class BattleRuntime {
    private BattleRuntime() {
    }

    public static AppliedActionResult applyAction(
            BattleRuntimeState state,
            BattleChoice choice,
            Predicate<GridCoord> canFit
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice is required");
        }

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        if (choice instanceof ShiftChoice shiftChoice) {
            return applyShift(state, actor, shiftChoice, canFit);
        }
        if (choice instanceof MoveChoice) {
            throw new UnsupportedOperationException("MoveChoice rule resolution is not ported yet; use applyResolvedMoveOutcome after authoritative rules resolve the move");
        }
        throw new IllegalArgumentException("unsupported battle choice: " + choice.getClass().getName());
    }

    /**
     * Applies an already-resolved direct combatant move to authoritative state.
     *
     * Accuracy and damage values must come from the Java core rule pipeline. This
     * boundary owns action consumption, HP mutation, and semantic event emission so
     * Minecraft/Cobblemon adapters can only render the authoritative outcome.
     */
    public static AppliedActionResult applyResolvedMoveOutcome(
            BattleRuntimeState state,
            MoveChoice choice,
            String source,
            AccuracyResult accuracy,
            DamageResult damage
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice is required");
        }
        if (accuracy == null) {
            throw new IllegalArgumentException("accuracy is required");
        }
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("resolved move outcome currently requires a combatant target");
        }
        if (accuracy.hit() && damage == null) {
            throw new IllegalArgumentException("damage is required for a hit");
        }

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
                source,
                actor.combatantId(),
                target.combatantId(),
                choice.moveId(),
                accuracy,
                accuracy.hit() ? damage : null,
                target.hp()
        );
        return new AppliedActionResult(List.of(event));
    }

    private static AppliedActionResult applyShift(
            BattleRuntimeState state,
            RuntimeCombatantState actor,
            ShiftChoice choice,
            Predicate<GridCoord> canFit
    ) {
        Set<GridCoord> legalDestinations = Movement.legalShiftTiles(
                state.grid(),
                actor.movementProfile(),
                0,
                canFit
        );
        ShiftApplicationResult result = ShiftApplication.apply(
                actor.combatantId(),
                actor.position(),
                choice.destination(),
                legalDestinations,
                actor.actionBudget()
        );
        actor.moveTo(result.position());
        return new AppliedActionResult(List.of(result.event()));
    }
}
