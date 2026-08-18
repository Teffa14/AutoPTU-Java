package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.StatResolution;

import java.util.Set;

/**
 * Minecraft-facing direct-move entrypoint that derives effective combat stats
 * and intrinsic move metadata from authoritative runtime state before delegating
 * to BattleRuntime.
 */
public final class RuntimeMoveResolution {
    private RuntimeMoveResolution() {
    }

    public static AppliedActionResult applyUsingStateStats(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            String actorSize,
            String targetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            String damageCategory,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        if (state == null) {
            throw new IllegalArgumentException("state is required");
        }
        if (choice == null) {
            throw new IllegalArgumentException("choice is required");
        }
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        int attackValue = StatResolution.offensive(
                actor.requireStatProfile(),
                damageCategory,
                ignorePositiveAttackStage
        );
        int defenseValue = StatResolution.defensive(
                target.requireStatProfile(),
                damageCategory,
                ignorePositiveDefenseStage
        );

        MoveResolutionInput resolvedInput = new MoveResolutionInput(
                input.moveAc(),
                input.evasion(),
                input.accuracyStage(),
                input.critRange(),
                input.meleeNoGuard(),
                input.blurApplies(),
                input.rerollOnMiss(),
                input.effectiveDb(),
                attackValue,
                defenseValue,
                input.sniper(),
                input.typeMultiplier(),
                input.modifiers()
        );

        return BattleRuntime.applyAuthoritativeMove(
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers,
                source,
                rng,
                resolvedInput
        );
    }

    /**
     * Preferred Minecraft boundary for direct damaging moves.
     *
     * AC, DB, critical range, and category are loaded from the authoritative
     * MoveOption. The remaining input contains stateful hook results that have not
     * yet been migrated into runtime state. This method intentionally ignores any
     * conflicting intrinsic move values present in that transitional input.
     */
    public static AppliedActionResult applyUsingStateStatsAndMoveMetadata(
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
            boolean ignorePositiveDefenseStage
    ) {
        if (move == null) {
            throw new IllegalArgumentException("move is required");
        }
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        MoveCombatProfile metadata = move.requireCombatProfile();
        MoveResolutionInput metadataBoundInput = new MoveResolutionInput(
                metadata.ac(),
                input.evasion(),
                input.accuracyStage(),
                metadata.critRange(),
                input.meleeNoGuard(),
                input.blurApplies(),
                input.rerollOnMiss(),
                metadata.damageBase(),
                input.attackValue(),
                input.defenseValue(),
                input.sniper(),
                input.typeMultiplier(),
                input.modifiers()
        );
        return applyUsingStateStats(
                state,
                choice,
                move,
                actorSize,
                targetSize,
                lineOfSightBlockers,
                source,
                rng,
                metadataBoundInput,
                metadata.damageCategory(),
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
    }
}
