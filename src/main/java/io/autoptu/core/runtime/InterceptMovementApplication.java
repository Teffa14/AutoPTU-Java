package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

/**
 * Commits the movement portion of an interception after geometry and the skill check
 * have already been resolved by the authoritative core.
 *
 * <p>The chosen intercept position must come from {@link InterceptGeometryResolution}
 * over server-owned legal Shift tiles. A failed check never mutates position. This
 * boundary deliberately does not spend the ordinary Shift action bucket because the
 * Python interception path performs this movement as part of the reaction.</p>
 */
public final class InterceptMovementApplication {
    private InterceptMovementApplication() {}

    public record Result(
            String interceptorId,
            GridCoord origin,
            GridCoord destination,
            boolean checkSucceeded,
            boolean moved
    ) {}

    public static Result apply(
            BattleRuntimeState state,
            String interceptorId,
            GridCoord interceptPosition,
            InterceptCheckResolution.Result checkResult
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (interceptorId == null || interceptorId.isBlank()) {
            throw new IllegalArgumentException("interceptorId is required");
        }
        if (interceptPosition == null) {
            throw new IllegalArgumentException("intercept position is required");
        }
        if (checkResult == null) {
            throw new IllegalArgumentException("intercept check result is required");
        }

        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);
        GridCoord origin = interceptor.position();
        if (!checkResult.success()) {
            return new Result(interceptorId, origin, origin, false, false);
        }

        boolean moved = !origin.equals(interceptPosition);
        if (moved) interceptor.moveTo(interceptPosition);
        return new Result(interceptorId, origin, interceptor.position(), true, moved);
    }
}
