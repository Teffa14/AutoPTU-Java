package io.autoptu.core.hook;

/**
 * Reusable phase-scoped status behavior.
 *
 * Implementations receive only authoritative lifecycle context and the canonical
 * normalized status name that matched the registration. They return semantic
 * events and/or a pending skip request; adapters never execute these rules.
 */
@FunctionalInterface
public interface StatusPhaseEffect {
    LifecycleHookResult apply(LifecycleHookContext context, String status);
}
