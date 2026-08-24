package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.hook.PreDamageFollowUpMoveExecutionScope;
import io.autoptu.core.hook.PreDamageFollowUpMoveExecutor;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.random.PythonRandom;

import java.util.Set;

/**
 * Preferred direct-move boundary when PRE-damage reactions may synchronously re-enter the
 * original move. All PTU state derivation remains in the Java core; adapters provide only the
 * already-selected action plus the same transitional legacy input used by RuntimeMoveResolution.
 */
public final class RuntimeMoveResolutionWithFollowUps {
    private RuntimeMoveResolutionWithFollowUps() {
    }

    public static AppliedActionResult applyUsingAuthoritativeCombatState(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        PreDamageFollowUpMoveExecutor followUpExecutor = RuntimePreDamageFollowUpMoveExecution.executor(
                state,
                move,
                source,
                rng,
                legacyInput,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
        return PreDamageFollowUpMoveExecutionScope.runWith(
                followUpExecutor,
                () -> RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                        state,
                        choice,
                        move,
                        actorSize,
                        targetSize,
                        lineOfSightBlockers,
                        source,
                        rng,
                        legacyInput,
                        ignorePositiveAttackStage,
                        ignorePositiveDefenseStage
                )
        );
    }
}
