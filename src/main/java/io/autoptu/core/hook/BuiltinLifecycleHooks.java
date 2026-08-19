package io.autoptu.core.hook;

import io.autoptu.core.runtime.BattleRuntime;

/** Built-in lifecycle registrations already backed by parity-safe Java behavior. */
public final class BuiltinLifecycleHooks {
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
                .build();
    }
}
