package io.autoptu.core.hook;

import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Objects;

/**
 * Declarative lifecycle hook for removing named temporary-effect families.
 *
 * The hook owns cleanup scope only. Timing and ordering remain explicit in the
 * LifecycleHookRegistry so Python-oracle sequencing stays visible and testable.
 */
public final class TemporaryEffectCleanupLifecycleHook implements LifecycleHook {
    public enum Scope {
        ACTOR,
        ALL_COMBATANTS
    }

    private final Scope scope;
    private final List<String> effectNames;

    public TemporaryEffectCleanupLifecycleHook(Scope scope, List<String> effectNames) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (effectNames == null || effectNames.isEmpty()) {
            throw new IllegalArgumentException("effectNames must not be empty");
        }
        this.effectNames = effectNames.stream()
                .map(name -> Objects.requireNonNull(name, "effect name").strip())
                .peek(name -> {
                    if (name.isEmpty()) throw new IllegalArgumentException("effect name must not be blank");
                })
                .distinct()
                .toList();
    }

    public Scope scope() {
        return scope;
    }

    public List<String> effectNames() {
        return effectNames;
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (scope == Scope.ALL_COMBATANTS) {
            for (String combatantId : context.state().combatantIds()) {
                cleanup(context.state().requireCombatant(combatantId));
            }
        } else {
            if (context.actorId() == null || context.actorId().isBlank()) {
                throw new IllegalArgumentException("ACTOR cleanup requires actorId");
            }
            cleanup(context.state().requireCombatant(context.actorId()));
        }
        return LifecycleHookResult.empty();
    }

    private void cleanup(RuntimeCombatantState combatant) {
        for (String effectName : effectNames) {
            combatant.temporaryEffects().removeAll(effectName);
        }
    }
}
