package io.autoptu.core.runtime;

import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.hook.BuiltinDamageModifierHooks;
import io.autoptu.core.hook.BuiltinEffectiveMoveHooks;
import io.autoptu.core.hook.BuiltinPostDamageHooks;
import io.autoptu.core.hook.BuiltinPreDamageReactionHooks;
import io.autoptu.core.hook.DamageModifierHookContext;
import io.autoptu.core.hook.DamageModifierHookRegistry;
import io.autoptu.core.hook.DamageModifierHookResult;
import io.autoptu.core.hook.EffectiveMoveHookContext;
import io.autoptu.core.hook.EffectiveMoveHookRegistry;
import io.autoptu.core.hook.EffectiveMoveHookResult;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.CombatantStatProfile;
import io.autoptu.core.model.EvasionProfile;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
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
 * move transformations, STAB, type effectiveness, damage modifiers, post-damage
 * ability effects, and intrinsic move metadata from authoritative runtime state
 * before delegating to BattleRuntime.
 */
public final class RuntimeMoveResolution {
    private static final EffectiveMoveHookRegistry EFFECTIVE_MOVE_HOOKS =
            BuiltinEffectiveMoveHooks.standardRegistry();
    private static final DamageModifierHookRegistry DAMAGE_MODIFIER_HOOKS =
            BuiltinDamageModifierHooks.standardRegistry();
    private static final PostDamageHookRegistry POST_DAMAGE_HOOKS =
            BuiltinPostDamageHooks.standardRegistry();
    private static final PreDamageReactionHookRegistry PRE_DAMAGE_HOOKS =
            BuiltinPreDamageReactionHooks.registry();

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
        return applyUsingAuthoritativeCombatState(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source, rng,
                input, ignorePositiveAttackStage, ignorePositiveDefenseStage,
                BattleRuntimeDependencies.empty()
        );
    }

    /** Dependency-aware authoritative boundary for production direct moves. */
    public static AppliedActionResult applyUsingAuthoritativeCombatState(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source, PythonRandom rng,
            MoveResolutionInput input, boolean ignorePositiveAttackStage, boolean ignorePositiveDefenseStage,
            BattleRuntimeDependencies dependencies
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        MoveCombatProfile metadata = move.requireCombatProfile();
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());

        EffectiveMoveHookResult effectiveMoveHooks = authoritativeEffectiveMoveHooks(
                state, choice, move, actor, target, metadata);
        MoveCombatProfile effectiveMetadata = effectiveMoveHooks.profile();

        MoveResolutionInput stateBoundInput = authoritativeStateBoundInput(
                state,
                choice,
                move,
                actor,
                target,
                effectiveMetadata,
                input,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
        DamageModifierHookResult damageHooks = authoritativeDamageHooks(
                state, choice, move, actor, target, effectiveMetadata);
        stateBoundInput = withModifiers(stateBoundInput, damageHooks.modifiers());
        MoveSpecialHookRegistry moveSpecialHooks = RuntimeMoveSpecialHooks.standardRegistry(move, effectiveMetadata);
        return BattleRuntime.applyAuthoritativeMove(state, choice, move, actorSize, targetSize,
                lineOfSightBlockers, source, rng, stateBoundInput,
                combineEvents(effectiveMoveHooks.events(), damageHooks.events()),
                moveSpecialHooks, PRE_DAMAGE_HOOKS, POST_DAMAGE_HOOKS, effectiveMetadata, dependencies);
    }

    /**
     * Executes one legal TILE declaration across the authoritative target expansion.
     * The action and frequency are spent exactly once, while every target independently
     * re-derives current PTU stats/hooks and consumes the same battle RNG in target order.
     */
    public static MultiTargetAppliedActionResult applyAreaUsingAuthoritativeCombatState(
            BattleRuntimeState state,
            MoveChoice tileChoice,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        return applyAreaUsingAuthoritativeCombatState(
                state, tileChoice, source, rng, legacyInput,
                ignorePositiveAttackStage, ignorePositiveDefenseStage,
                BattleRuntimeDependencies.empty()
        );
    }

    /** Dependency-aware authoritative boundary for production area moves. */
    public static MultiTargetAppliedActionResult applyAreaUsingAuthoritativeCombatState(
            BattleRuntimeState state,
            MoveChoice tileChoice,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage,
            BattleRuntimeDependencies dependencies
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (tileChoice == null) throw new IllegalArgumentException("tileChoice is required");
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (legacyInput == null) throw new IllegalArgumentException("legacyInput is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        if (tileChoice.targetMode() != ChoiceTargetMode.TILE || !tileChoice.targetId().isBlank()) {
            throw new IllegalArgumentException("multi-target move execution requires a TILE move choice");
        }

        EffectiveMoveTargetResolution targeting = RuntimeAreaMoveTargeting.resolve(state, tileChoice);
        MoveOption move = requireCanonicalMove(state, tileChoice.actorId(), tileChoice.moveId());
        RuntimeCombatantState actor = state.requireCombatant(tileChoice.actorId());
        if (!actor.actionBudget().consume(tileChoice.actionType(), tileChoice.moveId())) {
            throw new IllegalStateException(tileChoice.actionType().value() + " action is already consumed");
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        ArrayList<String> resolvedTargetIds = new ArrayList<>();
        for (String targetId : targeting.targetIds()) {
            RuntimeCombatantState target = state.requireCombatant(targetId);
            MoveChoice targetChoice = new MoveChoice(
                    tileChoice.actorId(),
                    tileChoice.moveId(),
                    ChoiceTargetMode.COMBATANT,
                    targetId,
                    target.position(),
                    tileChoice.actionType()
            );
            AppliedActionResult targetResult = applyAreaTargetUsingAuthoritativeCombatState(
                    state,
                    targetChoice,
                    move,
                    targeting.anchor(),
                    source,
                    rng,
                    legacyInput,
                    ignorePositiveAttackStage,
                    ignorePositiveDefenseStage,
                    dependencies
            );
            resolvedTargetIds.add(targetId);
            events.addAll(targetResult.events());
        }
        actor.moveFrequencyUsage().recordUse(move);
        return new MultiTargetAppliedActionResult(events, resolvedTargetIds);
    }

    /**
     * Preferred matured delayed-hit boundary for combatant targets.
     *
     * The delayed entry only preserves attacker, move and target identity. Effective move
     * metadata, stats, evasion, accuracy stage, status projections, STAB, type effectiveness,
     * damage modifiers and post-result hooks are re-derived from the current authoritative
     * BattleRuntimeState before the hit resolves. The scheduling action already spent action
     * economy and move frequency, so BattleRuntime executes this path without spending them
     * again.
     */
    public static AppliedActionResult applyDelayedUsingAuthoritativeCombatState(
            BattleRuntimeState state,
            DelayedHitBinding binding,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        return applyDelayedUsingAuthoritativeCombatState(
                state, binding, source, rng, legacyInput,
                ignorePositiveAttackStage, ignorePositiveDefenseStage,
                BattleRuntimeDependencies.empty()
        );
    }

    /** Dependency-aware authoritative boundary for matured delayed hits. */
    public static AppliedActionResult applyDelayedUsingAuthoritativeCombatState(
            BattleRuntimeState state,
            DelayedHitBinding binding,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage,
            BattleRuntimeDependencies dependencies
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (binding == null) throw new IllegalArgumentException("binding is required");
        if (legacyInput == null) throw new IllegalArgumentException("legacyInput is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        MoveChoice choice = binding.choice();
        MoveOption move = binding.move();
        MoveCombatProfile metadata = move.requireCombatProfile();
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());

        EffectiveMoveHookResult effectiveMoveHooks = authoritativeEffectiveMoveHooks(
                state, choice, move, actor, target, metadata);
        MoveCombatProfile effectiveMetadata = effectiveMoveHooks.profile();
        MoveResolutionInput stateBoundInput = authoritativeStateBoundInput(
                state,
                choice,
                move,
                actor,
                target,
                effectiveMetadata,
                legacyInput,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
        DamageModifierHookResult damageHooks = authoritativeDamageHooks(
                state, choice, move, actor, target, effectiveMetadata);
        stateBoundInput = withModifiers(stateBoundInput, damageHooks.modifiers());

        return BattleRuntime.applyDelayedAuthoritativeMove(
                state,
                binding,
                source,
                rng,
                stateBoundInput,
                combineEvents(effectiveMoveHooks.events(), damageHooks.events()),
                POST_DAMAGE_HOOKS,
                effectiveMetadata,
                dependencies
        );
    }

    private static AppliedActionResult applyAreaTargetUsingAuthoritativeCombatState(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            GridCoord areaAnchor,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage,
            BattleRuntimeDependencies dependencies
    ) {
        MoveCombatProfile metadata = move.requireCombatProfile();
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        EffectiveMoveHookResult effectiveMoveHooks = authoritativeEffectiveMoveHooks(
                state, choice, move, actor, target, metadata);
        MoveCombatProfile effectiveMetadata = effectiveMoveHooks.profile();
        MoveResolutionInput stateBoundInput = authoritativeStateBoundInput(
                state,
                choice,
                move,
                actor,
                target,
                effectiveMetadata,
                legacyInput,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
        DamageModifierHookResult damageHooks = authoritativeDamageHooks(
                state, choice, move, actor, target, effectiveMetadata);
        stateBoundInput = withModifiers(stateBoundInput, damageHooks.modifiers());
        return BattleRuntime.applyAuthoritativeAreaMoveTarget(
                state,
                choice,
                move,
                areaAnchor,
                source,
                rng,
                stateBoundInput,
                combineEvents(effectiveMoveHooks.events(), damageHooks.events()),
                PRE_DAMAGE_HOOKS,
                POST_DAMAGE_HOOKS,
                effectiveMetadata,
                dependencies
        );
    }

    private static MoveResolutionInput authoritativeStateBoundInput(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            RuntimeCombatantState actor,
            RuntimeCombatantState target,
            MoveCombatProfile effectiveMetadata,
            MoveResolutionInput legacyInput,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
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
        double typeMultiplier = authoritativeTypeMultiplier(effectiveMetadata, target, legacyInput.typeMultiplier());
        return new MoveResolutionInput(
                effectiveMetadata.ac(), evasion, actor.accuracyStage(), effectiveMetadata.critRange(), meleeNoGuard,
                target.blur(), actor.probabilityControl(), effectiveDb, attackValue,
                defenseValue, actor.sniper(), typeMultiplier, List.of()
        );
    }

    private static MoveResolutionInput withModifiers(
            MoveResolutionInput input,
            List<AttackModifier> modifiers
    ) {
        return new MoveResolutionInput(
                input.moveAc(), input.evasion(), input.accuracyStage(), input.critRange(),
                input.meleeNoGuard(), input.blurApplies(), input.rerollOnMiss(), input.effectiveDb(),
                input.attackValue(), input.defenseValue(), input.sniper(), input.typeMultiplier(), modifiers
        );
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

    private static MoveOption requireCanonicalMove(BattleRuntimeState state, String actorId, String moveId) {
        for (MoveOption move : state.moveOptions(actorId)) {
            if (move != null && move.moveId().equals(moveId)) return move;
        }
        throw new IllegalArgumentException("move is not present in the actor's canonical moveset: " + moveId);
    }

    private static boolean isMelee(MoveOption move) {
        String targetKind = move.spec().targetKind();
        return targetKind != null && targetKind.trim().equalsIgnoreCase("melee");
    }
}
