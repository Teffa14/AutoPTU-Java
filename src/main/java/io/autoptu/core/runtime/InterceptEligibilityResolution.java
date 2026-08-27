package io.autoptu.core.runtime;

/**
 * Python-compatible core eligibility contract for interception candidates.
 *
 * <p>This resolver deliberately owns only the candidate guards shared by prepared Intercept,
 * Weaponize, and Sentinel Stance. Runtime callers must derive status/coaching/controller state
 * from {@link BattleRuntimeState}; Minecraft/Cobblemon must not supply these values directly.</p>
 */
public final class InterceptEligibilityResolution {
    private InterceptEligibilityResolution() {}

    public enum BlockReason {
        NONE,
        FAINTED,
        INCAPACITATED,
        IMMOBILIZED,
        LOYALTY
    }

    public record Input(
            Integer loyalty,
            boolean sameController,
            boolean coachingAllowsIntercept,
            boolean fainted,
            boolean paralyzed,
            boolean stuck,
            boolean tripped,
            boolean sleeping,
            boolean flinched,
            boolean trapped
    ) {}

    public record Result(boolean allowed, BlockReason blockReason) {}

    public static Result resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("intercept input is required");

        if (input.fainted()) {
            return new Result(false, BlockReason.FAINTED);
        }
        if (input.paralyzed() || input.stuck() || input.tripped() || input.sleeping() || input.flinched()) {
            return new Result(false, BlockReason.INCAPACITATED);
        }
        if (input.trapped()) {
            return new Result(false, BlockReason.IMMOBILIZED);
        }

        if (input.coachingAllowsIntercept()) {
            return new Result(true, BlockReason.NONE);
        }

        Integer loyalty = input.loyalty();
        if (loyalty == null) {
            return new Result(true, BlockReason.NONE);
        }
        if (loyalty < 3) {
            return new Result(false, BlockReason.LOYALTY);
        }
        if (loyalty < 6 && !input.sameController()) {
            return new Result(false, BlockReason.LOYALTY);
        }
        return new Result(true, BlockReason.NONE);
    }
}
