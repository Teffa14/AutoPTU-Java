package io.autoptu.core.hook;

import java.util.Objects;

/** Lifecycle adapter for ordered actor/global TURN_END effect resolution. */
public final class TurnEndEffectHook implements LifecycleHook {
    private final TurnEndEffectRegistry registry;

    public TurnEndEffectHook(TurnEndEffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        return registry.resolve(Objects.requireNonNull(context, "context"));
    }
}
