package io.autoptu.core.runtime;

import java.util.List;

/**
 * Materializes interception check input from server-owned combatant state/content.
 *
 * <p>Skill ranks, Justified, Coaching, Survivalist, Naturewalk, and terrain context are derived
 * here so adapters cannot provide rule-critical conclusions.</p>
 */
final class RuntimeInterceptCheckInputFactory {
    private static final String JUSTIFIED_ERRATA = "Justified [Errata]";
    private static final String SURVIVALIST = "Survivalist";
    private static final int JUSTIFIED_INTERCEPT_BONUS = 4;

    private RuntimeInterceptCheckInputFactory() {}

    static RuntimeInterceptCheckApplication.Input fromState(
            BattleRuntimeState state,
            String interceptorId,
            CombatantRuleContent interceptorContent,
            int distance
    ) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (interceptorContent == null) {
            throw new IllegalArgumentException("interceptor rule content is required");
        }
        RuntimeCombatantState interceptor = state.requireCombatant(interceptorId);
        int justifiedBonus = interceptor.hasAbilityExact(JUSTIFIED_ERRATA)
                ? JUSTIFIED_INTERCEPT_BONUS
                : 0;
        boolean coachingAutomaticSuccess = interceptor.temporaryEffects().has("coaching_intercept");
        List<String> terrainContext = TerrainContextLabelResolver.resolve(state, interceptorId);
        int terrainBonus = TerrainSkillCheckBonusResolver.resolve(
                "Acrobatics",
                interceptorContent.hasTrainerFeature(SURVIVALIST),
                interceptorContent.effectiveNaturewalkLabels(),
                terrainContext
        );
        return RuntimeInterceptCheckApplication.fromServerOwnedSkills(
                distance,
                interceptorContent,
                justifiedBonus,
                terrainBonus,
                coachingAutomaticSuccess
        );
    }
}
