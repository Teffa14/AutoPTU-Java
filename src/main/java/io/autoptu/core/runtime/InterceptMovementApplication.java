package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;
import io.autoptu.core.rules.ForcedMovementInstruction;

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

    /**
     * Ordered melee-intercept follow-up matching the pinned Python oracle:
     * line/intercept movement, Push 1 of the protected target, then occupy the
     * protected target's original anchor. The forced-movement result is retained so
     * callers can emit collision/partial-stop events without recomputing geometry.
     */
    public record MeleeResult(
            Result interceptMovement,
            GridCoord protectedTargetOrigin,
            ForcedDisplacementResolution.Result targetPush,
            GridCoord interceptorDestination,
            GridCoord protectedTargetDestination
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

    public static MeleeResult applyMelee(
            BattleRuntimeState state,
            String interceptorId,
            String protectedTargetId,
            GridCoord interceptPosition,
            InterceptCheckResolution.Result checkResult
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (protectedTargetId == null || protectedTargetId.isBlank()) {
            throw new IllegalArgumentException("protectedTargetId is required");
        }
        if (interceptorId != null && interceptorId.strip().equals(protectedTargetId.strip())) {
            throw new IllegalArgumentException("interceptor and protected target must differ");
        }

        RuntimeCombatantState target = state.requireCombatant(protectedTargetId);
        GridCoord targetOrigin = target.position();
        Result interceptMovement = apply(state, interceptorId, interceptPosition, checkResult);
        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);

        if (!interceptMovement.checkSucceeded()) {
            return new MeleeResult(
                    interceptMovement,
                    targetOrigin,
                    null,
                    interceptor.position(),
                    target.position()
            );
        }

        ForcedDisplacementResolution.Result targetPush = ForcedMovementApplication.apply(
                state,
                interceptorId,
                protectedTargetId,
                new ForcedMovementInstruction(ForcedMovementInstruction.Kind.PUSH, 1)
        );

        // Python commits this assignment after apply_forced_movement without branching
        // on that helper's boolean result. Preserve that observable ordering exactly.
        interceptor.moveTo(targetOrigin);

        return new MeleeResult(
                interceptMovement,
                targetOrigin,
                targetPush,
                interceptor.position(),
                target.position()
        );
    }
}
