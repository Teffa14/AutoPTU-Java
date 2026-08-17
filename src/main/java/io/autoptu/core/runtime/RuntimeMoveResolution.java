package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.StatResolution;

import java.util.Set;

/**
 * Minecraft-facing direct-move entrypoint that derives effective combat stats
 * from authoritative runtime combatant state before delegating to BattleRuntime.
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
}
