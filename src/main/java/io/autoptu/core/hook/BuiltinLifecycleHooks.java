package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntime;
import io.autoptu.core.runtime.CombatantEntryAbilityLifecycleHook;
import io.autoptu.core.runtime.CombatantRuleContentRegistry;
import io.autoptu.core.runtime.DeclaredActionRoundLifecycleHook;
import io.autoptu.core.runtime.DelayedHitRoundLifecycleHook;
import io.autoptu.core.runtime.FieldRoundLifecycleHook;
import io.autoptu.core.runtime.HeldItemRuleCatalog;
import io.autoptu.core.runtime.InitialJoinedRoundLifecycleHook;
import io.autoptu.core.runtime.RoundStartAbilityLifecycleHook;
import io.autoptu.core.runtime.RoundStartTrainerFeatureLifecycleHook;
import io.autoptu.core.runtime.RoundTemporaryEffectExpiryHook;
import io.autoptu.core.runtime.RoundWindowHistoryLifecycleHook;
import io.autoptu.core.runtime.TrainerFeatureDispatchCatalog;
import io.autoptu.core.runtime.TrainerFeatureEffectRegistry;
import io.autoptu.core.runtime.TrainerRuntimeState;

import java.util.List;
import java.util.Map;

/** Built-in lifecycle registrations already backed by parity-safe Java behavior. */
public final class BuiltinLifecycleHooks {
    private static final List<String> ROUND_START_TEMPORARY_EFFECTS = List.of(
            "intercept_ready",
            "extra_action",
            "delayed",
            "riposte_ready"
    );

    private BuiltinLifecycleHooks() {}

    public static LifecycleHookRegistry registry() {
        return registry(new HeldItemRuleCatalog(Map.of()));
    }

    /**
     * Lifecycle registry with server-owned held-item rule metadata injected by the core bootstrap.
     * The default registry remains compatibility-safe until canonical item content is materialized.
     */
    public static LifecycleHookRegistry registry(HeldItemRuleCatalog heldItemRuleCatalog) {
        return registry(heldItemRuleCatalog, List.of(), new TrainerFeatureEffectRegistry());
    }

    /**
     * Lifecycle registry with canonical Trainer Feature content injected by the authoritative core.
     * Empty Trainer Feature and combatant rule content preserve compatibility for callers that have
     * not materialized those catalogs yet; adapters never supply or interpret these definitions.
     */
    public static LifecycleHookRegistry registry(
            HeldItemRuleCatalog heldItemRuleCatalog,
            List<TrainerFeatureDispatchCatalog.TrainerSpec> trainerFeatures,
            TrainerFeatureEffectRegistry trainerFeatureEffects
    ) {
        return registry(
                heldItemRuleCatalog,
                trainerFeatures,
                trainerFeatureEffects,
                CombatantRuleContentRegistry.empty()
        );
    }

    /**
     * Lifecycle registry with canonical PTU combatant content injected by the authoritative core.
     * Round-start abilities use this same snapshot for capability-dependent effects such as Arena Trap.
     */
    public static LifecycleHookRegistry registry(
            HeldItemRuleCatalog heldItemRuleCatalog,
            List<TrainerFeatureDispatchCatalog.TrainerSpec> trainerFeatures,
            TrainerFeatureEffectRegistry trainerFeatureEffects,
            CombatantRuleContentRegistry combatantRuleContent
    ) {
        if (heldItemRuleCatalog == null) throw new IllegalArgumentException("heldItemRuleCatalog is required");
        if (trainerFeatureEffects == null) throw new IllegalArgumentException("trainerFeatureEffects is required");
        if (combatantRuleContent == null) throw new IllegalArgumentException("combatantRuleContent is required");
        List<TrainerFeatureDispatchCatalog.TrainerSpec> safeTrainerFeatures =
                trainerFeatures == null ? List.of() : List.copyOf(trainerFeatures);

        CombatantPhaseEffectDispatcher phaseDispatcher = CombatantPhaseEffectDispatcher.builder()
                .family(
                        CombatantPhaseEffectFamily.STATUS,
                        new StatusPhaseLifecycleHook(BuiltinStatusPhaseEffects.registry())
                )
                .family(
                        CombatantPhaseEffectFamily.ABILITY,
                        new AbilityPhaseLifecycleHook(BuiltinAbilityPhaseEffects.lancerRegistry())
                )
                .family(
                        CombatantPhaseEffectFamily.PERK,
                        new PerkPhaseLifecycleHook(BuiltinPerkPhaseEffects.registry())
                )
                .build();

        StatusControllerPhaseEnvelopeDispatcher phaseEnvelope = StatusControllerPhaseEnvelopeDispatcher.builder()
                .step(
                        StatusControllerPhaseOrderingPolicy.Step.HELD_ITEM_START,
                        new HeldItemStartLifecycleHook(heldItemRuleCatalog)
                )
                .step(StatusControllerPhaseOrderingPolicy.Step.COMBATANT_PHASE_EFFECTS, phaseDispatcher)
                .build();

        return LifecycleHookRegistry.builder()
                .register(
                        "round-field-progression",
                        HookSource.TERRAIN,
                        LifecycleHookPoint.ROUND_START,
                        10,
                        new FieldRoundLifecycleHook()
                )
                .register(
                        "round-delayed-hit-maturity",
                        HookSource.MOVE,
                        LifecycleHookPoint.ROUND_START,
                        20,
                        new DelayedHitRoundLifecycleHook()
                )
                .register(
                        "round-follow-me-foresight-expiry",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.ROUND_START,
                        30,
                        new RoundTemporaryEffectExpiryHook()
                )
                .register(
                        "round-trainer-ap-action-reset",
                        HookSource.TRAINER_FEATURE,
                        LifecycleHookPoint.ROUND_START,
                        40,
                        context -> {
                            for (String trainerId : context.state().trainerIds()) {
                                TrainerRuntimeState trainer = context.state().requireTrainer(trainerId);
                                trainer.expireTemporaryAp(context.round());
                                trainer.resetActions();
                            }
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-temporary-effect-cleanup",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.ROUND_START,
                        45,
                        new TemporaryEffectCleanupLifecycleHook(
                                TemporaryEffectCleanupLifecycleHook.Scope.ALL_COMBATANTS,
                                ROUND_START_TEMPORARY_EFFECTS
                        )
                )
                .register(
                        "round-declared-action-cleanup",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START,
                        50,
                        new DeclaredActionRoundLifecycleHook()
                )
                .register(
                        "round-move-frequency-reset",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START,
                        100,
                        context -> {
                            BattleRuntime.resetRoundMoveFrequency(context.state());
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-damage-history-rotation",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START_POST_INITIATIVE,
                        700,
                        context -> {
                            context.damageHistory().rotateForNewRound();
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-injury-history-rotation",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START_POST_INITIATIVE,
                        710,
                        context -> {
                            context.injuryHistory().rotateForNewRound();
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-window-history-pruning",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START_POST_INITIATIVE,
                        720,
                        new RoundWindowHistoryLifecycleHook()
                )
                .register(
                        "round-initial-joined-state",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.ROUND_START_POST_INITIATIVE,
                        730,
                        new InitialJoinedRoundLifecycleHook()
                )
                .register(
                        "round-trainer-feature-dispatch",
                        HookSource.TRAINER_FEATURE,
                        LifecycleHookPoint.ROUND_START_EFFECTS,
                        100,
                        new RoundStartTrainerFeatureLifecycleHook(safeTrainerFeatures, trainerFeatureEffects)
                )
                .register(
                        "round-ability-dispatch",
                        HookSource.ABILITY,
                        LifecycleHookPoint.ROUND_START_EFFECTS,
                        110,
                        new RoundStartAbilityLifecycleHook(combatantRuleContent)
                )
                .register(
                        "combatant-entry-ability-dispatch",
                        HookSource.ABILITY,
                        LifecycleHookPoint.COMBATANT_ENTRY,
                        100,
                        new CombatantEntryAbilityLifecycleHook()
                )
                .register(
                        "combatant-turn-start-effects",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.TURN_START,
                        500,
                        phaseEnvelope
                )
                .register(
                        "combatant-phase-effects",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.PHASE_CHANGE,
                        500,
                        phaseEnvelope
                )
                .register(
                        "global-temporary-phase-effects",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.PHASE_CHANGE,
                        510,
                        new GlobalTemporaryEffectPhaseHook(BuiltinGlobalTemporaryEffects.registry())
                )
                .register(
                        "turn-extra-action-cleanup",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.TURN_END,
                        490,
                        new TemporaryEffectCleanupLifecycleHook(
                                TemporaryEffectCleanupLifecycleHook.Scope.ACTOR,
                                List.of("extra_action")
                        )
                )
                .register(
                        "turn-last-turn-round-refresh",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.TURN_END,
                        500,
                        new TemporaryEffectRefreshLifecycleHook(
                                TemporaryEffectRefreshLifecycleHook.Scope.ACTOR,
                                "last_turn_round",
                                (context, combatantId) -> Map.of("round", context.round())
                        )
                )
                .register(
                        "turn-end-effects",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.TURN_END,
                        510,
                        new TurnEndEffectHook(BuiltinTurnEndEffects.registry())
                )
                .build();
    }
}
