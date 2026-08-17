package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.ShiftApplicationResult;
import io.autoptu.core.rules.Movement;
import io.autoptu.core.rules.ShiftApplication;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * First authoritative action dispatcher for headless and Minecraft execution.
 *
 * The runtime revalidates choices from core state before mutation. Controllers may
 * select a BattleChoice, but they cannot directly set positions or spend actions.
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
            throw new UnsupportedOperationException("MoveChoice application is not ported yet");
        }
        throw new IllegalArgumentException("unsupported battle choice: " + choice.getClass().getName());
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
