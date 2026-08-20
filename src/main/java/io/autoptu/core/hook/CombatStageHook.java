package io.autoptu.core.hook;

/** One authoritative reaction to an already-applied PTU combat-stage change. */
@FunctionalInterface
public interface CombatStageHook {
    CombatStageHookResult resolve(CombatStageHookContext context);
}
