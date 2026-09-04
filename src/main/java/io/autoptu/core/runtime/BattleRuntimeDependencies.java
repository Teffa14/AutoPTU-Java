package io.autoptu.core.runtime;

import io.autoptu.core.hook.BuiltinStatusApplicationHooks;
import io.autoptu.core.hook.StatusApplicationHookRegistry;

/**
 * Immutable composition snapshot for authoritative runtime rule dependencies.
 *
 * <p>This object carries canonical PTU rule data and reusable hook registries into runtime
 * orchestration without storing adapter-owned state in {@link BattleRuntimeState} or growing
 * method signatures for each rule family. Rule conclusions remain in their dedicated resolvers;
 * this boundary only composes the authoritative PTU implementation used by every producer.</p>
 */
public record BattleRuntimeDependencies(
        CombatantRuleContentRegistry combatantRuleContent,
        StatusApplicationHookRegistry statusApplicationHooks
) {
    public BattleRuntimeDependencies {
        if (combatantRuleContent == null) {
            throw new IllegalArgumentException("combatant rule content registry is required");
        }
        if (statusApplicationHooks == null) {
            throw new IllegalArgumentException("status application hook registry is required");
        }
    }

    /**
     * Compatibility constructor for callers that only supply canonical combatant content.
     * Built-in PTU status hooks are still part of the authoritative runtime composition.
     */
    public BattleRuntimeDependencies(CombatantRuleContentRegistry combatantRuleContent) {
        this(combatantRuleContent, BuiltinStatusApplicationHooks.registry());
    }

    public static BattleRuntimeDependencies empty() {
        return new BattleRuntimeDependencies(
                CombatantRuleContentRegistry.empty(),
                BuiltinStatusApplicationHooks.registry()
        );
    }
}
