package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookResult;

/** Lifecycle adapter for server-owned per-round reaction usage. */
public final class ReactionUsageLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        context.state().reactionUsage().pruneForRoundFromLifecycle(context.round());
        return LifecycleHookResult.empty();
    }
}
