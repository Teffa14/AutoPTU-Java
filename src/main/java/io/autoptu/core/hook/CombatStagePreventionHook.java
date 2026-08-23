package io.autoptu.core.hook;

@FunctionalInterface
public interface CombatStagePreventionHook {
    CombatStagePreventionResult resolve(CombatStagePreventionContext context);
}
