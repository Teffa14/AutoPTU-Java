package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.runtime.RuntimeCombatantState;

import java.util.List;

/** Built-in combat-stage reactions frozen from the pinned Python oracle. */
public final class BuiltinCombatStageHooks {
    private BuiltinCombatStageHooks() {}

    public static CombatStageHookRegistry registry() {
        return CombatStageHookRegistry.builder()
                // Python registration order currently places Simple after Minus, Plus,
                // Defiant, and Competitive. Reserve order 50 so those families can be
                // added later without changing Simple's relative position.
                .register(
                        "ability.simple.post-apply",
                        HookSource.ABILITY,
                        CombatStageHookPhase.POST_APPLY,
                        50,
                        BuiltinCombatStageHooks::simpleDoublesAppliedStageChange
                )
                .build();
    }

    private static CombatStageHookResult simpleDoublesAppliedStageChange(CombatStageHookContext context) {
        if (context.appliedDelta() == 0) return CombatStageHookResult.empty();
        RuntimeCombatantState target = context.target();
        if (!target.hasAbilityExact("Simple")) return CombatStageHookResult.empty();

        int current = target.combatStages().get(context.stat());
        int next = target.combatStages().adjust(context.stat(), context.appliedDelta());
        int applied = next - current;
        if (applied == 0) return CombatStageHookResult.empty();

        RuleEffectEvent event = new RuleEffectEvent(
                "ability",
                "Simple",
                context.targetId(),
                context.targetId(),
                context.moveId(),
                "simple",
                applied,
                target.hp()
        );
        return CombatStageHookResult.events(List.of(event));
    }
}
