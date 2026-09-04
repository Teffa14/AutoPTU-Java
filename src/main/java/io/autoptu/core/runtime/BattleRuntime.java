package io.autoptu.core.runtime;

import io.autoptu.core.action.BattleChoice;
import io.autoptu.core.action.ChoiceTargetMode;
import io.autoptu.core.action.MoveChoice;
import io.autoptu.core.action.MoveOption;
import io.autoptu.core.action.ShiftChoice;
import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.BattleEventFactory;
import io.autoptu.core.event.MoveResolvedEvent;
import io.autoptu.core.event.StatusSkipEvent;
import io.autoptu.core.event.TrainerFeatureEvent;
import io.autoptu.core.hook.BuiltinPreDamageReactionHooks;
import io.autoptu.core.hook.MoveSpecialHookRegistry;
import io.autoptu.core.hook.MoveSpecialPreDamageResolution;
import io.autoptu.core.hook.PostDamageHookContext;
import io.autoptu.core.hook.PostDamageHookRegistry;
import io.autoptu.core.hook.PostDamageHookResult;
import io.autoptu.core.hook.PreDamageReactionContext;
import io.autoptu.core.hook.PreDamageReactionHookRegistry;
import io.autoptu.core.hook.PreDamageReactionResult;
import io.autoptu.core.hook.PreResolutionTargetHookRegistry;
import io.autoptu.core.model.AccuracyResult;
import io.autoptu.core.model.ActionType;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.model.GridCoord;
import io.autoptu.core.model.MoveCombatProfile;
import io.autoptu.core.model.ShiftApplicationResult;
import io.autoptu.core.model.TurnPhase;
import io.autoptu.core.random.PythonRandom;
import io.autoptu.core.rules.Accuracy;
import io.autoptu.core.rules.ActionBudget;
import io.autoptu.core.rules.DamageResolution;
import io.autoptu.core.rules.Movement;
import io.autoptu.core.rules.ShiftApplication;
import io.autoptu.core.rules.StatusSkipExceptionResolution;
import io.autoptu.core.rules.StatusSkipResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public final class BattleRuntime {
    private static final PreDamageReactionHookRegistry PRE_DAMAGE_REACTIONS = BuiltinPreDamageReactionHooks.registry();
    private static final MoveSpecialHookRegistry NO_MOVE_SPECIALS = MoveSpecialHookRegistry.builder().build();

    private BattleRuntime() {}

    public static AppliedActionResult applyAction(BattleRuntimeState state, BattleChoice choice, Predicate<GridCoord> canFit) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        if (choice instanceof ShiftChoice shiftChoice) return applyShift(state, actor, shiftChoice, canFit);
        if (choice instanceof MoveChoice) throw new UnsupportedOperationException("MoveChoice requires authoritative move context; use applyAuthoritativeMove");
        throw new IllegalArgumentException("unsupported battle choice: " + choice.getClass().getName());
    }

    public static AppliedActionResult applyStatusSkip(
            BattleRuntimeState state,
            String actorId,
            String status,
            TurnPhase phase,
            String reason
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (actorId == null || actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (phase == null) throw new IllegalArgumentException("phase is required");

        RuntimeCombatantState actor = state.requireCombatant(actorId);
        StatusSkipFeatureState featureState = state.statusSkipFeatures(actorId);
        StatusSkipExceptionResolution.Result exception = StatusSkipExceptionResolution.resolve(
                status,
                featureState.signatureModification(),
                featureState.signatureMove(),
                featureState.duelistsManualIgnoreStatus()
        );
        if (!exception.skipTurn()) {
            TrainerFeatureEvent event = trainerFeatureEvent(actor, status, exception);
            return new AppliedActionResult(List.of(event));
        }

        StatusSkipResolution.apply(actor.actionBudget());
        StatusSkipEvent event = new StatusSkipEvent(actor.combatantId(), status, phase, reason);
        return new AppliedActionResult(List.of(event));
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, input, List.of(), PostDamageHookResult.empty()
        );
    }

    /**
     * Public authoritative composition boundary for server-owned rule dependencies.
     * Existing callers remain source-compatible and receive an empty dependency snapshot.
     */
    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            BattleRuntimeDependencies dependencies
    ) {
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, List.of(), NO_MOVE_SPECIALS, PRE_DAMAGE_REACTIONS,
                PostDamageHookResult.empty(), null, null, null, true, true, false, dependencies
        );
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers,
                source, rng, input, preResolutionEvents, PostDamageHookResult.empty()
        );
    }

    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookResult postDamageHooks
    ) {
        if (postDamageHooks == null) throw new IllegalArgumentException("postDamageHooks is required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, NO_MOVE_SPECIALS, PRE_DAMAGE_REACTIONS,
                postDamageHooks, null, null, null, true, true
        );
    }

    /**
     * Preferred move path for stateful post-result effects. The registry executes only after
     * accuracy and ordinary DamageResolution have consumed the authoritative RNG. This matches
     * Python post_result hook timing while keeping HP/history mutation downstream of the final
     * signed damage adjustment.
     */
    public static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");
        if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, NO_MOVE_SPECIALS, PRE_DAMAGE_REACTIONS,
                null, postDamageHookRegistry, effectiveMetadata, null, true, true
        );
    }

    /** Runtime-package composition seam. Adapters use public authoritative boundaries. */
    static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PreDamageReactionHookRegistry preDamageHookRegistry,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, NO_MOVE_SPECIALS, preDamageHookRegistry,
                postDamageHookRegistry, effectiveMetadata
        );
    }

    /** Runtime-package composition seam for parity tests and move-special wiring. */
    static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            MoveSpecialHookRegistry moveSpecialHookRegistry,
            PreDamageReactionHookRegistry preDamageHookRegistry,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        return applyAuthoritativeMove(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, moveSpecialHookRegistry, preDamageHookRegistry,
                postDamageHookRegistry, effectiveMetadata, BattleRuntimeDependencies.empty()
        );
    }

    /** Dependency-aware runtime composition seam for the production move resolver. */
    static AppliedActionResult applyAuthoritativeMove(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            MoveSpecialHookRegistry moveSpecialHookRegistry,
            PreDamageReactionHookRegistry preDamageHookRegistry,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata,
            BattleRuntimeDependencies dependencies
    ) {
        if (moveSpecialHookRegistry == null) throw new IllegalArgumentException("moveSpecialHookRegistry is required");
        if (preDamageHookRegistry == null) throw new IllegalArgumentException("preDamageHookRegistry is required");
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");
        if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, moveSpecialHookRegistry, preDamageHookRegistry,
                null, postDamageHookRegistry, effectiveMetadata, null, true, true, false, dependencies
        );
    }

    /**
     * Runtime-only composition seam for reaction-driven target replacement. The declared choice
     * is validated against its controller-selected target before any PRE-target hook runs. The
     * effective target is then prepared from authoritative state and enters the ordinary move
     * pipeline without a second declaration check, while action economy and move frequency retain
     * their normal single-owner timing.
     */
    static AppliedActionResult applyAuthoritativeMoveWithPreResolutionTargets(
            BattleRuntimeState state,
            MoveChoice declaredChoice,
            MoveOption move,
            String actorSize,
            String declaredTargetSize,
            Set<GridCoord> lineOfSightBlockers,
            String source,
            PythonRandom rng,
            MoveResolutionInput legacyInput,
            PreResolutionTargetHookRegistry targetRegistry,
            MoveSpecialHookRegistry moveSpecialHookRegistry,
            PreDamageReactionHookRegistry preDamageHookRegistry,
            PostDamageHookRegistry postDamageHookRegistry,
            boolean ignorePositiveAttackStage,
            boolean ignorePositiveDefenseStage
    ) {
        if (targetRegistry == null) throw new IllegalArgumentException("targetRegistry is required");
        if (moveSpecialHookRegistry == null) throw new IllegalArgumentException("moveSpecialHookRegistry is required");
        if (preDamageHookRegistry == null) throw new IllegalArgumentException("preDamageHookRegistry is required");
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");

        RuntimePreResolutionMovePreparation.Result prepared = RuntimeValidatedPreResolutionMovePreparation.prepare(
                state,
                declaredChoice,
                move,
                actorSize,
                declaredTargetSize,
                lineOfSightBlockers,
                legacyInput,
                targetRegistry,
                ignorePositiveAttackStage,
                ignorePositiveDefenseStage
        );
        return applyAuthoritativeMoveInternal(
                state,
                prepared.effectiveChoice(),
                move,
                actorSize,
                declaredTargetSize,
                lineOfSightBlockers,
                source,
                rng,
                prepared.input(),
                prepared.preResolutionEvents(),
                moveSpecialHookRegistry,
                preDamageHookRegistry,
                null,
                postDamageHookRegistry,
                prepared.effectiveMetadata(),
                null,
                true,
                true,
                true
        );
    }

    /**
     * Runtime-only AoE target seam. The TILE action has already been revalidated and its ordered
     * targets expanded from authoritative state. Each target still receives the ordinary
     * accuracy/damage/PRE/post pipeline, but action economy and move frequency are owned by the
     * surrounding multi-target declaration and are therefore not spent here.
     */
    static AppliedActionResult applyAuthoritativeAreaMoveTarget(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            GridCoord areaAnchor,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PreDamageReactionHookRegistry preDamageHookRegistry,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        return applyAuthoritativeAreaMoveTarget(
                state, choice, move, areaAnchor, source, rng, input, preResolutionEvents,
                preDamageHookRegistry, postDamageHookRegistry, effectiveMetadata,
                BattleRuntimeDependencies.empty()
        );
    }

    /** Dependency-aware AoE target seam used by the authoritative production resolver. */
    static AppliedActionResult applyAuthoritativeAreaMoveTarget(
            BattleRuntimeState state,
            MoveChoice choice,
            MoveOption move,
            GridCoord areaAnchor,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PreDamageReactionHookRegistry preDamageHookRegistry,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata,
            BattleRuntimeDependencies dependencies
    ) {
        if (areaAnchor == null) throw new IllegalArgumentException("areaAnchor is required");
        if (preDamageHookRegistry == null) throw new IllegalArgumentException("preDamageHookRegistry is required");
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");
        if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        requireAreaResolvedCombatantChoice(state, choice, move);
        MoveSpecialHookRegistry moveSpecialHookRegistry = dependencies.moveSpecialHookRegistryFactory().create(
                move, effectiveMetadata, dependencies.statusApplicationHooks());
        return applyAuthoritativeMoveInternal(
                state, choice, move, "", "", Set.of(), source,
                rng, input, preResolutionEvents, moveSpecialHookRegistry, preDamageHookRegistry,
                null, postDamageHookRegistry, effectiveMetadata, areaAnchor, false, true, false, dependencies
        );
    }

    /**
     * Executes a matured delayed hit through the same accuracy, damage, RNG, post-result,
     * HP, history, and event pipeline as an ordinary move without spending action economy or
     * move frequency a second time. The scheduling action already owned that bookkeeping in
     * the pinned Python oracle.
     */
    public static AppliedActionResult applyDelayedAuthoritativeMove(
            BattleRuntimeState state,
            DelayedHitBinding binding,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata
    ) {
        return applyDelayedAuthoritativeMove(
                state, binding, source, rng, input, preResolutionEvents,
                postDamageHookRegistry, effectiveMetadata, BattleRuntimeDependencies.empty()
        );
    }

    /** Dependency-aware delayed-hit seam used by the authoritative production resolver. */
    public static AppliedActionResult applyDelayedAuthoritativeMove(
            BattleRuntimeState state,
            DelayedHitBinding binding,
            String source,
            PythonRandom rng,
            MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            PostDamageHookRegistry postDamageHookRegistry,
            MoveCombatProfile effectiveMetadata,
            BattleRuntimeDependencies dependencies
    ) {
        if (binding == null) throw new IllegalArgumentException("binding is required");
        if (postDamageHookRegistry == null) throw new IllegalArgumentException("postDamageHookRegistry is required");
        if (effectiveMetadata == null) throw new IllegalArgumentException("effectiveMetadata is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");
        requireDelayedCombatantBinding(state, binding);
        return applyAuthoritativeMoveInternal(
                state,
                binding.choice(),
                binding.move(),
                "",
                "",
                Set.of(),
                source,
                rng,
                input,
                preResolutionEvents,
                NO_MOVE_SPECIALS,
                PRE_DAMAGE_REACTIONS,
                null,
                postDamageHookRegistry,
                effectiveMetadata,
                null,
                false,
                false,
                false,
                dependencies
        );
    }

    private static AppliedActionResult applyAuthoritativeMoveInternal(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            MoveSpecialHookRegistry moveSpecialHookRegistry,
            PreDamageReactionHookRegistry preDamageReactionHooks,
            PostDamageHookResult precomputedPostDamageHooks,
            PostDamageHookRegistry deferredPostDamageHooks,
            MoveCombatProfile effectiveMetadata,
            GridCoord reactionAnchor,
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions
    ) {
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, moveSpecialHookRegistry, preDamageReactionHooks,
                precomputedPostDamageHooks, deferredPostDamageHooks, effectiveMetadata, reactionAnchor,
                spendOrdinaryMoveResources, runPreDamageReactions, false
        );
    }

    private static AppliedActionResult applyAuthoritativeMoveInternal(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            MoveSpecialHookRegistry moveSpecialHookRegistry,
            PreDamageReactionHookRegistry preDamageReactionHooks,
            PostDamageHookResult precomputedPostDamageHooks,
            PostDamageHookRegistry deferredPostDamageHooks,
            MoveCombatProfile effectiveMetadata,
            GridCoord reactionAnchor,
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions,
            boolean declaredChoiceAlreadyValidated
    ) {
        return applyAuthoritativeMoveInternal(
                state, choice, move, actorSize, targetSize, lineOfSightBlockers, source,
                rng, input, preResolutionEvents, moveSpecialHookRegistry, preDamageReactionHooks,
                precomputedPostDamageHooks, deferredPostDamageHooks, effectiveMetadata, reactionAnchor,
                spendOrdinaryMoveResources, runPreDamageReactions, declaredChoiceAlreadyValidated,
                BattleRuntimeDependencies.empty()
        );
    }

    private static AppliedActionResult applyAuthoritativeMoveInternal(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            PythonRandom rng, MoveResolutionInput input,
            List<? extends BattleEvent> preResolutionEvents,
            MoveSpecialHookRegistry moveSpecialHookRegistry,
            PreDamageReactionHookRegistry preDamageReactionHooks,
            PostDamageHookResult precomputedPostDamageHooks,
            PostDamageHookRegistry deferredPostDamageHooks,
            MoveCombatProfile effectiveMetadata,
            GridCoord reactionAnchor,
            boolean spendOrdinaryMoveResources,
            boolean runPreDamageReactions,
            boolean declaredChoiceAlreadyValidated,
            BattleRuntimeDependencies dependencies
    ) {
        if (rng == null) throw new IllegalArgumentException("rng is required");
        if (input == null) throw new IllegalArgumentException("input is required");
        if (moveSpecialHookRegistry == null) throw new IllegalArgumentException("moveSpecialHookRegistry is required");
        if (preDamageReactionHooks == null) throw new IllegalArgumentException("preDamageReactionHooks is required");
        if (dependencies == null) throw new IllegalArgumentException("runtime dependencies are required");

        if (spendOrdinaryMoveResources) {
            if (!declaredChoiceAlreadyValidated) {
                MoveChoiceRevalidation.requireLegalCombatantMove(state, choice, move, actorSize, targetSize, lineOfSightBlockers);
            }
        } else if (runPreDamageReactions) {
            requireAreaResolvedCombatantChoice(state, choice, move);
        } else {
            requireDelayedCombatantChoice(state, choice, move);
        }

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        int roll = rng.randIntInclusive(1, 20);
        AccuracyResult accuracy = Accuracy.resolve(input.accuracyCheck(roll, null));
        if (!accuracy.hit() && input.rerollOnMiss()) {
            actor.consumeProbabilityControl();
            int reroll = rng.randIntInclusive(1, 20);
            accuracy = Accuracy.resolve(input.accuracyCheck(roll, reroll));
        }

        DamageResult damage = accuracy.hit() ? DamageResolution.resolve(rng, input.damageCheck(accuracy.crit())) : null;
        double typeMultiplier = input.typeMultiplier();
        List<? extends BattleEvent> moveSpecialPreDamageEvents = List.of();
        Map<String, Object> moveSpecialResultSnapshot = null;
        String moveSpecialCategory = "";
        if (!moveSpecialHookRegistry.isEmpty() && accuracy.hit() && damage != null) {
            MoveCombatProfile specialMetadata = effectiveMetadata == null ? move.requireCombatProfile() : effectiveMetadata;
            moveSpecialCategory = specialMetadata.damageCategory();
            MoveSpecialPreDamageResolution.Result special = MoveSpecialPreDamageResolution.resolve(
                    moveSpecialHookRegistry,
                    state,
                    choice.actorId(),
                    choice.targetId(),
                    move.moveId(),
                    moveSpecialCategory,
                    true,
                    accuracy.crit(),
                    damage.damage(),
                    typeMultiplier,
                    accuracy.roll()
            );
            moveSpecialPreDamageEvents = special.events();
            moveSpecialResultSnapshot = special.resultSnapshot();
            typeMultiplier = special.typeMultiplier();
            accuracy = new AccuracyResult(special.hit(), special.crit(), accuracy.roll(), accuracy.needed());
            if (!special.hit()) {
                damage = null;
            } else if (special.damage() != damage.damage()) {
                damage = withFinalDamage(damage, special.damage());
            }
        }

        List<? extends BattleEvent> preDamageEvents = List.of();
        if (accuracy.hit() && runPreDamageReactions) {
            PreDamageReactionContext reactionContext = RuntimePreDamageReactionContextFactory.fromState(
                    state,
                    choice.actorId(),
                    choice.targetId(),
                    move.moveId(),
                    move.moveId(),
                    move.spec(),
                    reactionAnchor,
                    null
            );
            PreDamageReactionResult reaction = preDamageReactionHooks.resolve(
                    reactionContext,
                    PreDamageReactionResult.of(true, damage.damage(), typeMultiplier)
            );
            preDamageEvents = reaction.events();
            if (moveSpecialResultSnapshot != null) {
                moveSpecialResultSnapshot = MoveSpecialReactionHandoff.apply(moveSpecialResultSnapshot, reaction);
            }
            typeMultiplier = reaction.typeMultiplier();
            if (!reaction.hit()) {
                accuracy = new AccuracyResult(false, false, accuracy.roll(), accuracy.needed());
                damage = null;
            } else if (reaction.damage() != damage.damage()) {
                damage = withFinalDamage(damage, reaction.damage());
            }
        }

        PostDamageHookResult resolvedPostDamageHooks = precomputedPostDamageHooks == null
                ? PostDamageHookResult.empty()
                : precomputedPostDamageHooks;
        if (accuracy.hit() && deferredPostDamageHooks != null) {
            resolvedPostDamageHooks = deferredPostDamageHooks.resolve(new PostDamageHookContext(
                    state,
                    choice.actorId(),
                    choice.targetId(),
                    actor,
                    target,
                    move,
                    effectiveMetadata,
                    rng
            ));
        }
        if (accuracy.hit()) {
            damage = applyPostDamageAdjustment(damage, resolvedPostDamageHooks.flatDamageBonus());
            if (moveSpecialResultSnapshot != null) {
                moveSpecialResultSnapshot = MoveSpecialReactionHandoff.apply(
                        moveSpecialResultSnapshot, true, damage.damage(), typeMultiplier);
            }
        }

        int targetHpBeforeOutcome = target.hp();
        AppliedActionResult result = applyResolvedMoveOutcomeInternal(
                state, choice, source, accuracy, damage, spendOrdinaryMoveResources);
        if (accuracy.hit() && moveSpecialResultSnapshot != null) {
            result = RuntimeMoveSpecialPostDamageApplication.resolveAfterAppliedOutcome(
                    moveSpecialHookRegistry,
                    state,
                    choice,
                    move.moveId(),
                    moveSpecialCategory,
                    moveSpecialResultSnapshot,
                    true,
                    targetHpBeforeOutcome,
                    result
            ).actionResult();
        }
        if (accuracy.hit() && state.hasCanonicalMoves(choice.actorId())) {
            RuntimePostHitForcedMovementApplication.SemanticResolution forcedMovement =
                    RuntimePostHitForcedMovementApplication.resolveWithSemanticEvents(
                            state, choice, true, dependencies);
            if (!forcedMovement.events().isEmpty()) {
                ArrayList<BattleEvent> events = new ArrayList<>(result.events().size() + forcedMovement.events().size());
                events.addAll(result.events());
                events.addAll(forcedMovement.events());
                result = new AppliedActionResult(events);
            }
        }
        if (accuracy.hit()) {
            result = prependEvents(resolvedPostDamageHooks.events(), result);
        }
        result = prependEvents(preDamageEvents, result);
        result = prependEvents(moveSpecialPreDamageEvents, result);
        if (spendOrdinaryMoveResources) {
            actor.moveFrequencyUsage().recordUse(move);
        }
        return prependEvents(preResolutionEvents, result);
    }

    public static AppliedActionResult applyRevalidatedResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, MoveOption move, String actorSize,
            String targetSize, Set<GridCoord> lineOfSightBlockers, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        MoveChoiceRevalidation.requireLegalCombatantMove(state, choice, move, actorSize, targetSize, lineOfSightBlockers);
        AppliedActionResult result = applyResolvedMoveOutcome(state, choice, source, accuracy, damage);
        state.requireCombatant(choice.actorId()).moveFrequencyUsage().recordUse(move);
        return result;
    }

    public static AppliedActionResult applyResolvedMoveOutcome(
            BattleRuntimeState state, MoveChoice choice, String source,
            AccuracyResult accuracy, DamageResult damage
    ) {
        return applyResolvedMoveOutcomeInternal(state, choice, source, accuracy, damage, true);
    }

    private static AppliedActionResult applyResolvedMoveOutcomeInternal(
            BattleRuntimeState state,
            MoveChoice choice,
            String source,
            AccuracyResult accuracy,
            DamageResult damage,
            boolean spendAction
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (accuracy == null) throw new IllegalArgumentException("accuracy is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("resolved move outcome currently requires a combatant target");
        }
        if (accuracy.hit() && damage == null) throw new IllegalArgumentException("damage is required for a hit");

        RuntimeCombatantState actor = state.requireCombatant(choice.actorId());
        RuntimeCombatantState target = state.requireCombatant(choice.targetId());
        ActionBudget budget = actor.actionBudget();
        ActionType actionType = choice.actionType();
        if (spendAction && !budget.hasActionAvailable(actionType) && budget.extraCount(actionType) <= 0) {
            throw new IllegalStateException(actionType.value() + " action is already consumed");
        }

        int previousHp = target.hp();
        int resolvedDamage = accuracy.hit() ? Math.max(0, damage.damage()) : 0;
        int nextHp = Math.max(0, previousHp - resolvedDamage);
        if (spendAction && !budget.consume(actionType, choice.moveId())) {
            throw new IllegalStateException(actionType.value() + " action is already consumed");
        }
        target.setHp(nextHp);
        if (accuracy.hit()) {
            int actualDamage = Math.max(0, previousHp - target.hp());
            state.damageHistory().recordMoveHit(actor.combatantId(), target.combatantId(), actualDamage);
        }

        MoveResolvedEvent event = BattleEventFactory.moveResolved(
                source, actor.combatantId(), target.combatantId(), choice.moveId(),
                accuracy, accuracy.hit() ? damage : null, target.hp()
        );
        return new AppliedActionResult(List.of(event));
    }

    public static void resetRoundMoveFrequency(BattleRuntimeState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        for (String combatantId : state.combatantIds()) {
            state.requireCombatant(combatantId).moveFrequencyUsage().resetRound();
        }
    }

    private static void requireDelayedCombatantBinding(BattleRuntimeState state, DelayedHitBinding binding) {
        if (state == null) throw new IllegalArgumentException("state is required");
        requireDelayedCombatantChoice(state, binding.choice(), binding.move());
        if (!binding.entry().attackerId().equals(binding.choice().actorId())) {
            throw new IllegalArgumentException("delayed attacker does not match bound choice");
        }
    }

    private static void requireDelayedCombatantChoice(BattleRuntimeState state, MoveChoice choice, MoveOption move) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("delayed move execution currently requires a combatant target");
        }
        if (!choice.moveId().equals(move.moveId())) {
            throw new IllegalArgumentException("move metadata does not match delayed choice moveId");
        }
        state.requireCombatant(choice.actorId());
        state.requireCombatant(choice.targetId());
    }

    private static void requireAreaResolvedCombatantChoice(BattleRuntimeState state, MoveChoice choice, MoveOption move) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (choice == null) throw new IllegalArgumentException("choice is required");
        if (move == null) throw new IllegalArgumentException("move is required");
        if (choice.targetMode() != ChoiceTargetMode.COMBATANT || choice.targetId().isBlank()) {
            throw new IllegalArgumentException("area move target execution requires a combatant target");
        }
        if (!choice.moveId().equals(move.moveId()) || choice.actionType() != move.actionType()) {
            throw new IllegalArgumentException("move metadata does not match area target choice");
        }
        state.requireCombatant(choice.actorId());
        state.requireCombatant(choice.targetId());
    }

    private static DamageResult applyPostDamageAdjustment(DamageResult damage, int flatDamageAdjustment) {
        if (damage == null) throw new IllegalArgumentException("damage is required");
        if (flatDamageAdjustment == 0) return damage;
        int finalDamage = Math.max(0, Math.addExact(damage.damage(), flatDamageAdjustment));
        return withFinalDamage(damage, finalDamage);
    }

    private static DamageResult withFinalDamage(DamageResult damage, int finalDamage) {
        if (damage == null) throw new IllegalArgumentException("damage is required");
        return new DamageResult(
                damage.dice(),
                damage.baseRoll(),
                damage.criticalExtraRoll(),
                damage.damageRoll(),
                damage.preModifierDamage(),
                damage.preTypeDamage(),
                Math.max(0, finalDamage)
        );
    }

    private static AppliedActionResult prependEvents(
            List<? extends BattleEvent> before,
            AppliedActionResult result
    ) {
        if (before == null || before.isEmpty()) return result;
        ArrayList<BattleEvent> events = new ArrayList<>(before.size() + result.events().size());
        for (BattleEvent event : before) {
            if (event == null) throw new IllegalArgumentException("preResolutionEvents cannot contain null");
            events.add(event);
        }
        events.addAll(result.events());
        return new AppliedActionResult(events);
    }

    private static TrainerFeatureEvent trainerFeatureEvent(
            RuntimeCombatantState actor,
            String status,
            StatusSkipExceptionResolution.Result exception
    ) {
        return switch (exception.exceptionKind()) {
            case SUPREME_CONCENTRATION -> new TrainerFeatureEvent(
                    actor.combatantId(), "Signature Technique", "supreme_concentration",
                    exception.signatureMove(), status, actor.hp()
            );
            case DUELISTS_MANUAL -> new TrainerFeatureEvent(
                    actor.combatantId(), "Duelist's Manual", "ignore_status_skip",
                    "", status, actor.hp()
            );
            case NONE -> throw new IllegalStateException("status skip exception expected");
        };
    }

    private static AppliedActionResult applyShift(
            BattleRuntimeState state, RuntimeCombatantState actor,
            ShiftChoice choice, Predicate<GridCoord> canFit
    ) {
        Set<GridCoord> legalDestinations = Movement.legalShiftTiles(state.grid(), actor.movementProfile(), 0, canFit);
        ShiftApplicationResult result = ShiftApplication.apply(
                actor.combatantId(), actor.position(), choice.destination(),
                legalDestinations, actor.actionBudget()
        );
        actor.moveTo(result.position());
        return new AppliedActionResult(List.of(result.event()));
    }
}