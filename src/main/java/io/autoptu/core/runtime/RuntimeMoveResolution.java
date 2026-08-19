package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinDamageModifierHooks;
import io.autoptu.core.hook.BuiltinEffectiveMoveHooks;
import io.autoptu.core.hook.DamageModifierHookContext;
import io.autoptu.core.hook.DamageModifierHookRegistry;
import io.autoptu.core.hook.DamageModifierHookResult;
import io.autoptu.core.hook.EffectiveMoveHookContext;
import io.autoptu.core.hook.EffectiveMoveHookRegistry;
import io.autoptu.core.hook.EffectiveMoveHookResult;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.random.PythonRandom;
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
 * evasion, accuracy stage, Sniper, No Guard, Blur, Probability Control, pre-damage
 * move transformations, STAB, type effectiveness, damage modifiers, and intrinsic
 * move metadata from authoritative runtime state before delegating to BattleRuntime.
 */
public final class RuntimeMoveResolution {
    private static final EffectiveMoveHookRegistry EFFECTIVE_MOVE_HOOKS =
            BuiltinEffectiveMoveHooks.standardRegistry();
    private static final DamageModifierHookRegistry DAMAGE_MODIFIER_HOOKS =
            BuiltinDamageModifierHooks.standardRegistry();

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
        int attackValue = StatResolution.offensive(actor.effectiveStatProfile(), damageCategory, ignorePositiveAttackStage);
        int defenseValue = StatResolution.defensive(target.effectiveStatProfile(), damageCategory, ignorePositiveDefenseStage);
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

        EffectiveMoveHookResult effectiveMoveHooks = authoritativeEffectiveMoveHooks(
                state, choice, move, actor, target, metadata);
        MoveCombatProfile effectiveMetadata = effectiveMoveHooks.profile();

        CombatantStatProfile actorStats = StatusStatResolution.apply(
                actor.effectiveStatProfile(), state.statuses(choice.actorId()));
        CombatantStatProfile targetStats = StatusStatResolution.apply(
                target.effectiveStatProfile(), state.statuses(choice.targetId()));
        EvasionProfile authoritativeEvasion = StatusEvasionResolution.apply(
                target.requireEvasionProfile(), state.statuses(choice.targetId()));
        int evasion = EvasionResolution.resolve(authoritativeEvasion, effectiveMetadata.damageCategory());
        int attackValue = StatResolution.offensive(actorStats, effectiveMetadata.damageCategory(), ignorePositiveAttackStage);
        int defenseValue = StatResolution.defensive(targetStats, effectiveMetadata.damageCategory(), ignorePositiveDefenseStage);
        boolean meleeNoGuard = isMelee(move) && (actor.noGuard() || target.noGuard());
        int effectiveDb = authoritativeStabDamageBase(move, effectiveMetadata, actor);
        double typeMultiplier = authoritativeTypeMultiplier(effectiveMetadata, target, input.typeMultiplier());
        DamageModifierHookResult damageHooks = authoritativeDamageHooks(
                state, choice, move, actor, target, effectiveMetadata);
        MoveResolutionInput stateBoundInput = new MoveResolutionInput(
                effectiveMetadata.ac(), evasion, actor.accuracyStage(), effectiveMetadata.critRange(), meleeNoGuard,
                target.blur(), actor.probabilityControl(), effectiveDb, attackValue,
                defenseValue, actor.sniper(), typeMultiplier, damageHooks.modifiers()
        );
        return BattleRuntime.applyAuthoritativeMove(state, choice, move, actorSize, targetSize,
                lineOfSightBlockers, source, rng, stateBoundInput,
                combineEvents(effectiveMoveHooks.events(), damageHooks.events()));
    }

    private static EffectiveMoveHookResult authoritativeEffectiveMoveHooks(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            MoveCombatProfile metadata
    ) {
        return EFFECTIVE_MOVE_HOOKS.resolve(new EffectiveMoveHookContext(
                state,
                choice.actorId(),
                choice.targetId(),
                actor,
                target,
                move,
                metadata,
                metadata
        ));
    }

    private static int authoritativeStabDamageBase(MoveOption move, MoveCombatProfile metadata, RuntimeCombatantState actor) {
        if (metadata.moveType() == null || actor.types().isEmpty()) return metadata.damageBase();
        return StabResolution.resolve(metadata.damageBase(), move.moveId(), metadata.moveType(), actor.types());
    }

    private static double authoritativeTypeMultiplier(MoveCombatProfile metadata, RuntimeCombatantState target, double legacyMultiplier) {
        if (metadata.moveType() == null || target.types().isEmpty()) return legacyMultiplier;
        return PtuTables.typeMultiplier(metadata.moveType(), target.types());
    }

    private static DamageModifierHookResult authoritativeDamageHooks(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            MoveCombatProfile metadata
    ) {
        ArrayList<AttackModifier> resolved = new ArrayList<>();
        for (AttackModifier modifier : actor.damageModifiers()) {
            if (modifier != null && !"burned".equalsIgnoreCase(modifier.slug())) resolved.add(modifier);
        }
        DamageModifierHookContext hookContext = new DamageModifierHookContext(
                state,
                choice.actorId(),
                choice.targetId(),
                actor,
                target,
                move,
                metadata
        );
        DamageModifierHookResult hookResult = DAMAGE_MODIFIER_HOOKS.resolve(hookContext);
        resolved.addAll(hookResult.modifiers());
        return DamageModifierHookResult.of(resolved, hookResult.events());
    }

    private static List<BattleEvent> combineEvents(
            List<? extends BattleEvent> first,
            List<? extends BattleEvent> second
    ) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) return List.of();
        ArrayList<BattleEvent> events = new ArrayList<>();
        if (first != null) events.addAll(first);
        if (second != null) events.addAll(second);
        return List.copyOf(events);
    }

    private static boolean isMelee(MoveOption move) {
        String targetKind = move.spec().targetKind();
        return targetKind != null && targetKind.trim().equalsIgnoreCase("melee");
    }
}
