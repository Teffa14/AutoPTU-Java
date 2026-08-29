package io.autoptu.core.runtime;

/**
 * Runtime boundary that consumes the battle-owned Python-compatible RNG for an interception
 * check and immediately feeds the result into the deterministic check arithmetic.
 *
 * <p>The entry point is package-private on purpose. Adapters cannot provide a resolved d20 or
 * advance the battle RNG; orchestration inside the authoritative runtime owns both operations.</p>
 */
public final class RuntimeInterceptCheckApplication {
    private RuntimeInterceptCheckApplication() {}

    public record Input(
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

    /**
     * Builds the deterministic check input from server-owned PTU skill content.
     *
     * <p>The remaining modifiers stay explicit because they are resolved by separate authoritative
     * rule families. This helper deliberately prevents callers from supplying Acrobatics/Athletics
     * conclusions independently from the combatant content snapshot.</p>
     */
    static Input fromServerOwnedSkills(
            int distance,
            CombatantRuleContent interceptorContent,
            int justifiedBonus,
            int terrainBonus,
            boolean coachingAutomaticSuccess
    ) {
        if (interceptorContent == null) {
            throw new IllegalArgumentException("interceptor rule content is required");
        }
        return new Input(
                distance,
                interceptorContent.skillRank("Acrobatics"),
                interceptorContent.skillRank("Athletics"),
                justifiedBonus,
                terrainBonus,
                coachingAutomaticSuccess
        );
    }

    static InterceptCheckResolution.Result resolve(BattleRuntimeState state, Input input) {
        if (state == null) throw new IllegalArgumentException("battle state is required");
        if (input == null) throw new IllegalArgumentException("intercept check input is required");

        int roll = state.delayedHitStateFromRuntime().randomFromRuntime().randIntInclusive(1, 20);
        return InterceptCheckResolution.resolve(new InterceptCheckResolution.Input(
                roll,
                input.distance(),
                input.acrobaticsRank(),
                input.athleticsRank(),
                input.justifiedBonus(),
                input.terrainBonus(),
                input.coachingAutomaticSuccess()
        ));
    }
}
