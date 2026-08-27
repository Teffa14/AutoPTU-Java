package io.autoptu.core.runtime;

import java.util.Locale;

/**
 * Reusable pre-geometry gate for an interception attempt.
 *
 * <p>The policy owns attack-shape and Priority/Interrupt restrictions before candidate
 * ordering, skill checks, or movement occur. All inputs must be derived from canonical
 * battle/move state by the runtime; adapters must not decide intercept legality.</p>
 */
public final class InterceptAttemptPolicy {
    private InterceptAttemptPolicy() {}

    public enum BlockReason {
        NONE,
        CANNOT_MISS,
        AREA_ATTACK,
        UNSUPPORTED_TARGET_KIND,
        PRIORITY_SPEED
    }

    public record Input(
            boolean cannotMiss,
            boolean areaAttack,
            String targetKind,
            boolean priorityOrInterrupt,
            int interceptorSpeed,
            int attackerSpeed
    ) {
        public Input {
            targetKind = targetKind == null ? "" : targetKind.strip().toLowerCase(Locale.ROOT);
        }
    }

    public record Result(boolean allowed, BlockReason blockReason) {}

    public static Result resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        if (input.cannotMiss()) return blocked(BlockReason.CANNOT_MISS);
        if (input.areaAttack()) return blocked(BlockReason.AREA_ATTACK);
        if (!input.targetKind().equals("melee") && !input.targetKind().equals("ranged")) {
            return blocked(BlockReason.UNSUPPORTED_TARGET_KIND);
        }
        if (input.priorityOrInterrupt() && input.interceptorSpeed() <= input.attackerSpeed()) {
            return blocked(BlockReason.PRIORITY_SPEED);
        }
        return new Result(true, BlockReason.NONE);
    }

    private static Result blocked(BlockReason reason) {
        return new Result(false, reason);
    }
}
