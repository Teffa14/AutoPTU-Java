package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.BuiltinDamageModifierResolution;
import io.autoptu.core.rules.EvasionResolution;
import io.autoptu.core.rules.PtuTables;
import io.autoptu.core.rules.StabResolution;
import io.autoptu.core.rules.StatResolution;
import io.autoptu.core.rules.StatusEvasionResolution;
import io.autoptu.core.rules.StatusStatResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Minecraft-facing direct-move entrypoint that derives effective combat stats,
 * evasion, accuracy stage, Sniper, No Guard, Blur, Probability Control, STAB,
 * type effectiveness, damage modifiers, and intrinsic move metadata from
 * authoritative runtime state before delegating to BattleRuntime.
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
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        int attackValue = StatResolution.offensive(actor.requireStatProfile(), damageCategory, ignorePositiveAttackStage);
        int defenseValue = StatResolution.defensive(target.requireStatProfile(), damageCategory, ignorePositiveDefenseStage);
        MoveResolutionInput resolvedInput = new MoveResolutionInput(
                input.moveAc(), input.evasion(), input.accuracyStage(), input.critRange(),
                input.meleeNoGuard(), input.blurApplies(), input.rerollOnMiss(), input.effectiveDb(),
                attackValue, defenseValue, input.sniper(), input.typeMultiplier(), input.modifiers()
        );
        return BattleRuntime.applyAuthoritativeMove(state, choice, move, actorSize, targetSize,
                lineOfSightBlockers, source, rng, resolvedInput);
    }

    public static AppliedActionResult applyUsingStateStatsAndMoveMetadata(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source, PythonRandom rng,
            MoveResolutionInput input, boolean ignorePositiveAttackStage, boolean ignorePositiveDefenseStage
    ) {
        if (move == null) throw new IllegalArgumentException("move is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        MoveCombatProfile metadata = move.requireCombatProfile();
        MoveResolutionInput metadataBoundInput = new MoveResolutionInput(
                metadata.ac(), input.evasion(), input.accuracyStage(), metadata.critRange(),
                input.meleeNoGuard(), input.blurApplies(), input.rerollOnMiss(), metadata.damageBase(),
                input.attackValue(), input.defenseValue(), input.sniper(), input.typeMultiplier(), input.modifiers()
        );
        return applyUsingStateStats(state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, metadataBoundInput, metadata.damageCategory(),
                ignorePositiveAttackStage, ignorePositiveDefenseStage);
    }

    public static AppliedActionResult applyUsingAuthoritativeEvasion(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source, PythonRandom rng,
            MoveResolutionInput input, boolean ignorePositiveAttackStage, boolean ignorePositiveDefenseStage
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        MoveCombatProfile metadata = move.requireCombatProfile();
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        int evasion = EvasionResolution.resolve(target.requireEvasionProfile(), metadata.damageCategory());
        MoveResolutionInput stateBoundInput = new MoveResolutionInput(
                input.moveAc(), evasion, input.accuracyStage(), input.critRange(), input.meleeNoGuard(),
                input.blurApplies(), input.rerollOnMiss(), input.effectiveDb(), input.attackValue(),
                input.defenseValue(), input.sniper(), input.typeMultiplier(), input.modifiers()
        );
        return applyUsingStateStatsAndMoveMetadata(state, choice, move, actorSize, targetSize,
                lineOfSightBlockers, source, rng, stateBoundInput,
                ignorePositiveAttackStage, ignorePositiveDefenseStage);
    }

    /** Preferred direct-move boundary for Minecraft autobattles. */
    public static AppliedActionResult applyUsingAuthoritativeCombatState(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source, PythonRandom rng,
            MoveResolutionInput input, boolean ignorePositiveAttackStage, boolean ignorePositiveDefenseStage
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        MoveCombatProfile metadata = move.requireCombatProfile();
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        CombatantStatProfile actorStats = StatusStatResolution.apply(
                actor.requireStatProfile(), state.statuses(choice.actorId()));
        CombatantStatProfile targetStats = StatusStatResolution.apply(
                target.requireStatProfile(), state.statuses(choice.targetId()));
        EvasionProfile authoritativeEvasion = StatusEvasionResolution.apply(
                target.requireEvasionProfile(), state.statuses(choice.targetId()));
        int evasion = EvasionResolution.resolve(authoritativeEvasion, metadata.damageCategory());
        int attackValue = StatResolution.offensive(actorStats, metadata.damageCategory(), ignorePositiveAttackStage);
        int defenseValue = StatResolution.defensive(targetStats, metadata.damageCategory(), ignorePositiveDefenseStage);
        boolean meleeNoGuard = isMelee(move) && (actor.noGuard() || target.noGuard());
        int effectiveDb = authoritativeStabDamageBase(move, metadata, actor);
        double typeMultiplier = authoritativeTypeMultiplier(metadata, target, input.typeMultiplier());
        List<AttackModifier> damageModifiers = authoritativeDamageModifiers(state, choice.actorId(), actor, metadata);
        MoveResolutionInput stateBoundInput = new MoveResolutionInput(
                metadata.ac(), evasion, actor.accuracyStage(), metadata.critRange(), meleeNoGuard,
                target.blur(), actor.probabilityControl(), effectiveDb, attackValue,
                defenseValue, actor.sniper(), typeMultiplier, damageModifiers
        );
        return BattleRuntime.applyAuthoritativeMove(state, choice, move, actorSize, targetSize,
                lineOfSightBlockers, source, rng, stateBoundInput);
    }

    private static int authoritativeStabDamageBase(MoveOption move, MoveCombatProfile metadata, RuntimeCombatantState actor) {
        if (metadata.moveType() == null || actor.types().isEmpty()) return metadata.damageBase();
        return StabResolution.resolve(metadata.damageBase(), move.moveId(), metadata.moveType(), actor.types());
    }

    private static double authoritativeTypeMultiplier(MoveCombatProfile metadata, RuntimeCombatantState target, double legacyMultiplier) {
        if (metadata.moveType() == null || target.types().isEmpty()) return legacyMultiplier;
        return PtuTables.typeMultiplier(metadata.moveType(), target.types());
    }

    private static List<AttackModifier> authoritativeDamageModifiers(
            BattleRuntimeState state, String actorId, RuntimeCombatantState actor, MoveCombatProfile metadata
    ) {
        ArrayList<AttackModifier> resolved = new ArrayList<>();
        for (AttackModifier modifier : actor.damageModifiers()) {
            if (modifier != null && !"burned".equalsIgnoreCase(modifier.slug())) resolved.add(modifier);
        }
        resolved.addAll(BuiltinDamageModifierResolution.resolve(metadata.damageCategory(), state.statuses(actorId)));
        return List.copyOf(resolved);
    }

    private static boolean isMelee(MoveOption move) {
        String targetKind = move.spec().targetKind();
        return targetKind != null && targetKind.trim().equalsIgnoreCase("melee");
    }
}
