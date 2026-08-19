package io.autoptu.core.hook;

import java.util.Objects;

/** Lifecycle adapter for the reusable phase-scoped ability registry. */
public final class AbilityPhaseLifecycleHook implements LifecycleHook {
    private final AbilityPhaseEffectRegistry registry;

    public AbilityPhaseLifecycleHook(AbilityPhaseEffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        return registry.resolve(context);
    }
}
