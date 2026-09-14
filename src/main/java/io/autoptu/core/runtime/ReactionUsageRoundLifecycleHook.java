package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookResult;

/** Lifecycle adapter for pruning server-owned per-round reaction usage. */
public final class ReactionUsageRoundLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        context.state().pruneReactionUsageFromLifecycle();
        return LifecycleHookResult.empty();
    }
}
