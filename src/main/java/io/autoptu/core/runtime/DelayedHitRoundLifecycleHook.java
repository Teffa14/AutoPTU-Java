package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;

/** Resolves due combatant-target delayed hits at Python-compatible ROUND_START ordering. */
public final class DelayedHitRoundLifecycleHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context.point() != LifecycleHookPoint.ROUND_START) {
            throw new IllegalArgumentException("delayed hit maturity only runs at ROUND_START");
        }
        return LifecycleHookResult.events(
                DelayedHitLifecycleExecutor.resolveDueCombatantHits(context.state(), context.round())
        );
    }
}
