package io.autoptu.core.runtime;

import io.autoptu.core.hook.LifecycleHook;
import io.autoptu.core.hook.LifecycleHookContext;
import io.autoptu.core.hook.LifecycleHookPoint;
import io.autoptu.core.hook.LifecycleHookResult;

/** Expires Follow Me and Foresight immediately after delayed-hit maturity at ROUND_START. */
public final class RoundTemporaryEffectExpiryHook implements LifecycleHook {
    @Override
    public LifecycleHookResult apply(LifecycleHookContext context) {
        if (context.point() != LifecycleHookPoint.ROUND_START) {
            throw new IllegalArgumentException("round temporary expiry only runs at ROUND_START");
        }
        RoundTemporaryEffectExpiry.expireFamily(context.state(), context.round(), "follow_me");
        RoundTemporaryEffectExpiry.expireFamily(context.state(), context.round(), "foresight");
        return LifecycleHookResult.empty();
    }
}
