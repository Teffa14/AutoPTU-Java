package io.autoptu.core.hook;

import java.util.Objects;

/** Lifecycle adapter for the reusable status phase registry. */
public final class StatusPhaseLifecycleHook implements LifecycleHook {
    private final StatusPhaseEffectRegistry registry;

    public StatusPhaseLifecycleHook(StatusPhaseEffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        return registry.resolve(context);
    }
}
