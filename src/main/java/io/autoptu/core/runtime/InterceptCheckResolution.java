package io.autoptu.core.runtime;

/**
 * Deterministic arithmetic for the Python interception skill check.
 *
 * <p>Callers must derive skill ranks, Justified, terrain, Coaching and the d20 from
 * server-owned battle state/RNG. Minecraft/Cobblemon must not supply resolved PTU bonuses.</p>
 */
public final class InterceptCheckResolution {
    private InterceptCheckResolution() {}

    public record Input(
            int roll,
            int distance,
            int acrobaticsRank,
            int athleticsRank,
            int justifiedBonus,
            int terrainBonus,
            boolean coachingAutomaticSuccess
    ) {
        public Input {
            if (distance < 0) throw new IllegalArgumentException("distance cannot be negative");
        }
    }

    public record Result(
            int roll,
            int skillBonus,
            int justifiedBonus,
            int terrainBonus,
            int total,
            int dc,
            boolean success,
            boolean coachingAutomaticSuccess
    ) {}

    public static Result resolve(Input input) {
        if (input == null) throw new IllegalArgumentException("intercept check input is required");

        int skillBonus = Math.max(input.acrobaticsRank(), input.athleticsRank());
        int total = input.roll() + skillBonus + input.justifiedBonus() + input.terrainBonus();
        int dc = input.distance() * 3;
        boolean success = input.coachingAutomaticSuccess() || total >= dc;

        return new Result(
                input.roll(),
                skillBonus,
                input.justifiedBonus(),
                input.terrainBonus(),
                total,
                dc,
                success,
                input.coachingAutomaticSuccess()
        );
    }
}
