package io.autoptu.core.runtime;

/**
 * Language-neutral contract for how a matured delayed hit enters the battle engine.
 *
 * <p>The pinned Python oracle sends due delayed hits directly to target resolution,
 * bypassing the ordinary player/AI move-action entrypoint. Java keeps that distinction
 * explicit so lifecycle execution cannot accidentally spend action economy or move
 * frequency a second time merely by reusing the normal action path.</p>
 */
public record DelayedHitExecutionPolicy(
        EntryPoint entryPoint,
        boolean forwardsTargetId,
        boolean forwardsTargetPosition
) {
    public DelayedHitExecutionPolicy {
        if (entryPoint == null) {
            throw new IllegalArgumentException("entryPoint is required");
        }
    }

    public static DelayedHitExecutionPolicy targetResolution() {
        return new DelayedHitExecutionPolicy(EntryPoint.TARGET_RESOLUTION, true, true);
    }

    public enum EntryPoint {
        TARGET_RESOLUTION
    }
}
