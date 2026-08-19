package io.autoptu.core.hook;

import java.util.Objects;

/** Bridges the phase-scoped perk registry into the generic lifecycle dispatcher. */
public final class PerkPhaseLifecycleHook implements LifecycleHook {
    private final PerkPhaseEffectRegistry registry;

    public PerkPhaseLifecycleHook(PerkPhaseEffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        return registry.resolve(context);
    }
}
