package io.autoptu.core.hook;

import java.util.Objects;

/** Lifecycle adapter for global phase-scoped temporary-effect resolution. */
public final class GlobalTemporaryEffectPhaseHook implements LifecycleHook {
    private final GlobalTemporaryEffectPhaseRegistry registry;

    public GlobalTemporaryEffectPhaseHook(GlobalTemporaryEffectPhaseRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        return registry.resolve(Objects.requireNonNull(context, "context"));
    }
}
