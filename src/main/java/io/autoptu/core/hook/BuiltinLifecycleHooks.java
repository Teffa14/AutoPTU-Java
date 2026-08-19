package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntime;
import io.autoptu.core.runtime.RuntimeCombatantState;

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
        return LifecycleHookRegistry.builder()
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
                        "round-temporary-effect-cleanup",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.ROUND_START,
                        500,
                        context -> {
                            for (String combatantId : context.state().combatantIds()) {
                                RuntimeCombatantState combatant = context.state().requireCombatant(combatantId);
                                for (String effectName : ROUND_START_TEMPORARY_EFFECTS) {
                                    combatant.temporaryEffects().removeAll(effectName);
                                }
                            }
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-damage-history-rotation",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START,
                        700,
                        context -> {
                            context.damageHistory().rotateForNewRound();
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "round-injury-history-rotation",
                        HookSource.SYSTEM,
                        LifecycleHookPoint.ROUND_START,
                        710,
                        context -> {
                            context.injuryHistory().rotateForNewRound();
                            return LifecycleHookResult.empty();
                        }
                )
                .register(
                        "turn-temporary-effect-cleanup",
                        HookSource.TEMPORARY_EFFECT,
                        LifecycleHookPoint.TURN_END,
                        500,
                        context -> {
                            RuntimeCombatantState actor = context.state().requireCombatant(context.actorId());
                            actor.temporaryEffects().removeAll("extra_action");
                            actor.temporaryEffects().removeAll("last_turn_round");
                            actor.temporaryEffects().add("last_turn_round", Map.of("round", context.round()));
                            return LifecycleHookResult.empty();
                        }
                )
                .build();
    }
}
