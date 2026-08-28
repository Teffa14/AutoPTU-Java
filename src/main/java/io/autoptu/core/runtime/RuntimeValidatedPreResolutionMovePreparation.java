package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
import io.autoptu.core.model.GridCoord;

import java.util.Set;

/**
 * Server-owned boundary between declared move legality and target-scoped resolution preparation.
 *
 * <p>The controller declaration is revalidated exactly once against the target that was actually
 * declared. Only after that gate succeeds may PRE-target hooks replace the defender and rebuild
 * defender-bound accuracy/damage inputs. The replacement is deliberately not revalidated as a
 * second declaration: interception and later redirect families are reactions to a legal action,
 * not a new controller choice.</p>
 */
final class RuntimeValidatedPreResolutionMovePreparation {
    private RuntimeValidatedPreResolutionMovePreparation() {}

    static RuntimePreResolutionMovePreparation.Result prepare(
            BattleRuntimeState state,
            MoveChoice declaredChoice,
            MoveOption move,
            String actorSize,
            String declaredTargetSize,
            Set<GridCoord> lineOfSightBlockers,
            MoveResolutionInput legacyInput,
            PreResolutionTargetHookRegistry targetRegistry,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        if (lineOfSightBlockers == null) throw new IllegalArgumentException("lineOfSightBlockers is required");

        MoveChoiceRevalidation.requireLegalCombatantMove(
                state,
                declaredChoice,
                move,
                actorSize,
                declaredTargetSize,
                lineOfSightBlockers
        );
        return RuntimePreResolutionMovePreparation.prepare(
                state,
                declaredChoice,
                move,
                legacyInput,
                targetRegistry,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
    }
}
