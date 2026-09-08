package io.autoptu.core.runtime;

import java.util.Objects;

/**
 * Shared server-owned inputs available to round-start ability handlers.
 *
 * <p>The context keeps authoritative battle state and canonical rule content together so
 * ability registries can resolve capabilities and other content without accepting adapter-owned
 * maps or adding rule-specific parameters to the lifecycle controller.</p>
 */
public record RoundStartAbilityExecutionContext(
        BattleRuntimeState state,
        CombatantRuleContentRegistry ruleContent
) {
    public RoundStartAbilityExecutionContext {
        state = Objects.requireNonNull(state, "state");
        ruleContent = Objects.requireNonNull(ruleContent, "ruleContent");
    }

    public static RoundStartAbilityExecutionContext of(
            BattleRuntimeState state,
            CombatantRuleContentRegistry ruleContent
    ) {
        return new RoundStartAbilityExecutionContext(state, ruleContent);
    }
}
