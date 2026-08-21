package io.autoptu.core.runtime;

/**
 * Language-neutral contract for how a matured delayed hit enters the battle engine.
 *
 * <p>The pinned Python oracle sends due delayed hits first to target resolution. That
 * resolver then re-enters the ordinary move-action resolver. Keeping both steps explicit
 * prevents Java from inventing a lower-level execution path whose action/frequency
 * semantics could diverge from Python.</p>
 */
public record DelayedHitExecutionPolicy(
        EntryPoint entryPoint,
        boolean forwardsTargetId,
        boolean forwardsTargetPosition,
        boolean targetResolutionReentersMoveAction
) {
    public DelayedHitExecutionPolicy {
        if (entryPoint == null) {
            throw new IllegalArgumentException("entryPoint is required");
        }
    }

    public static DelayedHitExecutionPolicy targetResolution() {
        return new DelayedHitExecutionPolicy(EntryPoint.TARGET_RESOLUTION, true, true, true);
    }

    public enum EntryPoint {
        TARGET_RESOLUTION
    }
}
