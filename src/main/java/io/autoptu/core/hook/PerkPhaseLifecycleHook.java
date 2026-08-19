package io.autoptu.core.hook;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Bridges the phase-scoped perk registry into the generic lifecycle dispatcher. */
public final class PerkPhaseLifecycleHook implements LifecycleHook {
    private final PerkPhaseEffectRegistry registry;
    private final Function<LifecycleHookContext, Collection<String>> transitionalTrainerFeatureProjection;

    /**
     * Preferred server-authoritative boundary.
     *
     * Trainer Feature ownership is resolved from BattleRuntimeState using the current
     * combatant's canonical controller binding. Missing trainer state fails closed for
     * named perks while still allowing global perk hooks.
     */
    public PerkPhaseLifecycleHook(PerkPhaseEffectRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.transitionalTrainerFeatureProjection = null;
    }

    /**
     * Transitional boundary retained for existing callers/tests while trainer state is
     * adopted across the runtime. New Minecraft/Cobblemon integration must use the
     * server-authoritative constructor above.
     */
    public PerkPhaseLifecycleHook(
            PerkPhaseEffectRegistry registry,
            Function<LifecycleHookContext, Collection<String>> trainerFeatureProjection
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.transitionalTrainerFeatureProjection = Objects.requireNonNull(
                trainerFeatureProjection,
                "trainerFeatureProjection"
        );
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        Collection<String> trainerFeatures;
        if (transitionalTrainerFeatureProjection != null) {
            trainerFeatures = transitionalTrainerFeatureProjection.apply(context);
        } else if (!context.actorId().isBlank() && context.state().hasCanonicalTrainer(context.actorId())) {
            trainerFeatures = context.state().trainerFeatures(context.actorId());
        } else {
            trainerFeatures = List.of();
        }
        return registry.resolve(context, trainerFeatures);
    }
}
