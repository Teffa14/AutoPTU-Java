package io.autoptu.core.hook;

/** One phase-scoped Trainer Feature/perk rule resolved inside the authoritative core. */
@FunctionalInterface
public interface PerkPhaseEffect {
    LifecycleHookResult apply(LifecycleHookContext context, String perkName);
}
