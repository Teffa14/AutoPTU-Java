package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntime;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;

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
                .build();
    }
}
