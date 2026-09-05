package io.autoptu.core.hook;

import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Declarative lifecycle hook that replaces one temporary-effect family with one fresh entry.
 *
 * <p>Python lifecycle code frequently removes every prior marker and then writes one marker
 * carrying the current round. This hook preserves that mutation shape while leaving lifecycle
 * timing and ordering explicit in {@link LifecycleHookRegistry}.</p>
 */
public final class TemporaryEffectRefreshLifecycleHook implements LifecycleHook {
    public enum Scope {
        ACTOR,
        ALL_COMBATANTS
    }

    @FunctionalInterface
    public interface PayloadFactory {
        Map<String, ?> payload(LifecycleHookContext context, String combatantId);
    }

    private final Scope scope;
    private final String effectName;
    private final PayloadFactory payloadFactory;

    public TemporaryEffectRefreshLifecycleHook(
            Scope scope,
            String effectName,
            PayloadFactory payloadFactory
    ) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (effectName == null || effectName.isBlank()) {
            throw new IllegalArgumentException("effectName is required");
        }
        this.effectName = effectName.strip();
        this.payloadFactory = Objects.requireNonNull(payloadFactory, "payloadFactory");
    }

    public Scope scope() {
        return scope;
    }

    public String effectName() {
        return effectName;
    }

    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        Objects.requireNonNull(context, "context");
        if (scope == Scope.ALL_COMBATANTS) {
            for (String combatantId : context.state().combatantIds()) {
                refresh(context, combatantId);
            }
        } else {
            if (context.actorId() == null || context.actorId().isBlank()) {
                throw new IllegalArgumentException("ACTOR refresh requires actorId");
            }
            refresh(context, context.actorId());
        }
        return LifecycleHookResult.empty();
    }

    private void refresh(LifecycleHookContext context, String combatantId) {
        RuntimeCombatantState combatant = context.state().requireCombatant(combatantId);
        Map<String, ?> payload = payloadFactory.payload(context, combatantId);
        combatant.temporaryEffects().removeAll(effectName);
        combatant.temporaryEffects().add(effectName, payload == null ? Map.of() : payload);
    }
}
