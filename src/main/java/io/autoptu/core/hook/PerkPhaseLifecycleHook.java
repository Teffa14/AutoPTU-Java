package io.autoptu.core.hook;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

/** Bridges the phase-scoped perk registry into the generic lifecycle dispatcher. */
public final class PerkPhaseLifecycleHook implements LifecycleHook {
    private final PerkPhaseEffectRegistry registry;
    private final Function<LifecycleHookContext, Collection<String>> trainerFeatureProjection;

    public PerkPhaseLifecycleHook(
            PerkPhaseEffectRegistry registry,
            Function<LifecycleHookContext, Collection<String>> trainerFeatureProjection
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.trainerFeatureProjection = Objects.requireNonNull(trainerFeatureProjection, "trainerFeatureProjection");
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        return registry.resolve(context, trainerFeatureProjection.apply(context));
    }
}
