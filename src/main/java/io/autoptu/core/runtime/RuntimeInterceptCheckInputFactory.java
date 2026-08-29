package io.autoptu.core.runtime;

/**
 * Materializes interception check input from server-owned combatant state/content.
 *
 * <p>Skill ranks, Justified, and Coaching are derived here so adapters cannot provide
 * those rule-critical conclusions. Terrain remains an explicit internal input until
 * its authoritative environment contract is frozen independently against Python.</p>
 */
final class RuntimeInterceptCheckInputFactory {
    private static final String JUSTIFIED_ERRATA = "Justified [Errata]";
    private static final int JUSTIFIED_INTERCEPT_BONUS = 4;

    private RuntimeInterceptCheckInputFactory() {}

    static RuntimeInterceptCheckApplication.Input fromState(
            BattleRuntimeState state,
            String interceptorId,
            CombatantRuleContent interceptorContent,
            int distance,
            int terrainBonus
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);
        int justifiedBonus = interceptor.hasAbilityExact(JUSTIFIED_ERRATA)
                ? JUSTIFIED_INTERCEPT_BONUS
                : 0;
        boolean coachingAutomaticSuccess = interceptor.temporaryEffects().has("coaching_intercept");
        return RuntimeInterceptCheckApplication.fromServerOwnedSkills(
                distance,
                interceptorContent,
                justifiedBonus,
                terrainBonus,
                coachingAutomaticSuccess
        );
    }
}
