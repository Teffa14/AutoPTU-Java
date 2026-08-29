package io.autoptu.core.runtime;

/**
 * Materializes interception check input from server-owned combatant state/content.
 *
 * <p>Skill ranks and Coaching are derived here so adapters cannot provide those
 * rule-critical conclusions. Justified and terrain remain explicit internal inputs
 * until their authoritative source families are frozen independently against Python.</p>
 */
final class RuntimeInterceptCheckInputFactory {
    private RuntimeInterceptCheckInputFactory() {}

    static RuntimeInterceptCheckApplication.Input fromState(
            BattleRuntimeState state,
            String interceptorId,
            CombatantRuleContent interceptorContent,
            int distance,
            int justifiedBonus,
            int terrainBonus
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);
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
