package io.autoptu.core.runtime;

/**
 * Immutable composition snapshot for authoritative runtime rule dependencies.
 *
 * <p>This object carries canonical PTU rule data into runtime orchestration without storing
 * adapter-owned state in {@link BattleRuntimeState} or growing method signatures for each rule
 * family. Future shared registries can join this boundary while their rule conclusions remain in
 * their dedicated resolvers.</p>
 */
public record BattleRuntimeDependencies(
        CombatantRuleContentRegistry combatantRuleContent
) {
    public BattleRuntimeDependencies {
        if (combatantRuleContent == null) {
            throw new IllegalArgumentException("combatant rule content registry is required");
        }
    }

    public static BattleRuntimeDependencies empty() {
        return new BattleRuntimeDependencies(CombatantRuleContentRegistry.empty());
    }
}
