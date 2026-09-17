package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.random.PythonRandom;

import java.util.Set;

/**
 * Production move-execution boundary that keeps reaction-relevant completion projection
 * inside the authoritative core.
 *
 * <p>Adapters should call this boundary rather than reconstructing ranged occurrences from
 * rendered Minecraft/Cobblemon state. RuntimeMoveResolution remains the PTU resolver; this
 * class only composes its authoritative result with the canonical completion projection.</p>
 */
public final class RuntimeMoveExecution {
    private RuntimeMoveExecution() {
    }

    public static AppliedActionResult applyDirect(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage,
            BattleRuntimeDependencies dependencies
    ) {
        AppliedActionResult resolved = RuntimeMoveResolution.applyUsingAuthoritativeCombatState(
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers,
                source,
                rng,
                input,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage,
                dependencies
        );
        return RuntimeMoveCompletionProjection.direct(resolved, choice, move);
    }

    public static MultiTargetAppliedActionResult applyArea(
            BattleRuntimeState state,
            MoveChoice tileChoice,
            MoveOption move,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage,
            BattleRuntimeDependencies dependencies
    ) {
        if (move == null) throw new IllegalArgumentException("move is required");
        if (!move.moveId().equals(tileChoice.moveId())) {
            throw new IllegalArgumentException("move must match tile choice");
        }
        MultiTargetAppliedActionResult resolved = RuntimeMoveResolution.applyAreaUsingAuthoritativeCombatState(
                state,
                tileChoice,
                source,
                rng,
                input,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage,
                dependencies
        );
        return RuntimeMoveCompletionProjection.area(resolved, tileChoice, move);
    }
}