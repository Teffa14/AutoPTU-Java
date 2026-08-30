package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.Collection;
import java.util.Set;

/**
 * Server-owned composition of Intercept line geometry with legal Shift destinations.
 *
 * <p>The caller supplies only the attack-line cells produced by PTU targeting geometry.
 * Combatant position, footprint and Shift legality are read from {@link BattleRuntimeState}.
 * This keeps Minecraft/Cobblemon adapters from selecting the intercept destination.</p>
 */
public final class RuntimeInterceptPositionResolver {
    private RuntimeInterceptPositionResolver() {}

    public static GridCoord resolve(
            BattleRuntimeState state,
            String interceptorId,
            Collection<GridCoord> attackLine
    ) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (interceptorId == null || interceptorId.isBlank()) {
            throw new IllegalArgumentException("interceptorId is required");
        }

        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);
        InterceptGeometryResolution.Candidate geometry = new InterceptGeometryResolution.Candidate(
                interceptorId,
                interceptor.position(),
                state.geometry(interceptorId).sizeLabel()
        );
        Set<GridCoord> legalShiftTiles = RuntimeShiftDestinationResolver.legalShiftTiles(
                state,
                interceptorId,
                0
        );
        return InterceptGeometryResolution.nearestReachableAttackLineTile(
                geometry,
                attackLine,
                legalShiftTiles
        );
    }
}
