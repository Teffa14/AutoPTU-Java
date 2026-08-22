package io.autoptu.core.runtime;

/**
 * Language-neutral contract for how a matured delayed hit enters the battle engine.
 *
 * <p>The pinned Python oracle sends due delayed hits first to target resolution. That
 * resolver then re-enters the ordinary move-action resolver. Keeping both steps explicit
 * prevents Java from inventing a lower-level execution path whose action/frequency
 * semantics could diverge from Python. Both stored target id and target position are
 * forwarded unchanged; target resolution does not rewrite the move into Tile targeting.
 * When the stored target id still resolves, Python uses the defender's live position.
 * When that defender no longer exists, Python falls back to the stored target position
 * while preserving the move's original targeting model. From that anchor, Python
 * recomputes the move area, selects combatants by footprint overlap, rechecks line of
 * sight, and keeps the explicit target id as the preferred target when it still exists.</p>
 */
public record DelayedHitExecutionPolicy(
        EntryPoint entryPoint,
        boolean forwardsTargetId,
        boolean forwardsTargetPosition,
        boolean targetResolutionReentersMoveAction,
        boolean targetPositionForcesTile,
        boolean usesLiveDefenderPositionWhenPresent,
        boolean recomputesAffectedTiles,
        boolean selectsTargetsByFootprintOverlap,
        boolean rechecksLineOfSight,
        boolean explicitTargetIdHasPriority,
        boolean fallsBackToStoredTargetPositionWhenDefenderMissing
) {
    public DelayedHitExecutionPolicy {
        if (entryPoint == null) {
            throw new IllegalArgumentException("entryPoint is required");
        }
    }

    public static DelayedHitExecutionPolicy targetResolution() {
        return new DelayedHitExecutionPolicy(
                EntryPoint.TARGET_RESOLUTION,
                true,
                true,
                true,
                false,
                true,
                true,
                true,
                true,
                true,
                true
        );
    }

    public enum EntryPoint {
        TARGET_RESOLUTION
    }
}
