package io.autoptu.core.hook;

/** One phase-scoped ability behavior executed from authoritative battle state. */
@FunctionalInterface
public interface AbilityPhaseEffect {
    LifecycleHookResult apply(LifecycleHookContext context, String registeredAbility);
}
