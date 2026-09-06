package io.autoptu.core.runtime;

/**
 * Server-owned classification of the canonical HP transition after ordinary damage.
 *
 * <p>Runtime combatants materialize faint state through canonical HP rather than a second
 * mutable boolean. This resolver gives lifecycle/content hooks one shared contract for
 * detecting the alive-to-zero transition so they can order semantic events without
 * reimplementing faint detection.</p>
 */
public final class RuntimePostDamageOutcomeResolution {
    private RuntimePostDamageOutcomeResolution() {}

    public static Result resolve(int hpBefore, int hpAfter) {
        if (hpBefore < 0 || hpAfter < 0) {
            throw new IllegalArgumentException("HP values cannot be negative");
        }
        if (hpAfter > hpBefore) {
            throw new IllegalArgumentException("post-damage HP cannot exceed pre-damage HP");
        }

        boolean faintedBefore = hpBefore == 0;
        boolean faintedAfter = hpAfter == 0;
        return new Result(faintedBefore, faintedAfter, !faintedBefore && faintedAfter);
    }

    public record Result(boolean faintedBefore, boolean faintedAfter, boolean transitionedToFainted) {
        public Result {
            if (transitionedToFainted && (faintedBefore || !faintedAfter)) {
                throw new IllegalArgumentException("faint transition must be alive-to-fainted");
            }
        }
    }
}
