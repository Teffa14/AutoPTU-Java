package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookResult;

/** Clears Python-compatible battle.declared_actions during ROUND_START. */
public final class DeclaredActionRoundLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        context.state().clearDeclaredActionsFromLifecycle();
        return LifecycleHookResult.empty();
    }
}
