package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookResult;

/** Lifecycle adapter for declarative round-window history pruning. */
public final class RoundWindowHistoryLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        context.state().roundWindowHistories().pruneForRoundFromLifecycle(context.round());
        return LifecycleHookResult.empty();
    }
}
