package io.autoptu.core.runtime;

import io.autoptu.core.hook.StatusApplicationContext;
import io.autoptu.core.hook.StatusApplicationHookRegistry;
import io.autoptu.core.hook.StatusApplicationHookResult;

import java.util.Objects;

/** Server-authoritative status mutation boundary used by moves, abilities, items and terrain. */
public final class StatusApplicationResolution {
    private StatusApplicationResolution() {
    }

    public static StatusApplicationResult apply(
            BattleRuntimeState state,
            StatusApplicationHookRegistry hooks,
            String sourceActorId,
            String targetId,
            StatusEntry status,
            String sourceKind,
            String sourceName,
            String moveId
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(hooks, "hooks");
        Objects.requireNonNull(status, "status");

        StatusApplicationContext context = new StatusApplicationContext(
                state, sourceActorId, targetId, status, sourceKind, sourceName, moveId
        );
        StatusApplicationHookResult hookResult = hooks.resolve(context);
        if (hookResult.blocked()) {
            return new StatusApplicationResult(false, status, hookResult.events());
        }
        state.putStatus(targetId, status);
        return new StatusApplicationResult(true, status, hookResult.events());
    }
}
