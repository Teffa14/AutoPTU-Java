package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.CombatStat;
import io.autoptu.core.runtime.CombatStageMutationOptions;
import io.autoptu.core.runtime.CombatStageMutationResult;
import io.autoptu.core.runtime.CombatStageMutationService;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.SpatialAbilityQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Built-in combat-stage reactions frozen from the pinned Python oracle. */
public final class BuiltinCombatStageHooks {
    static final String MINUS_SWSH_HOOK_ID = "ability.minus-swsh.post-apply";
    static final String PLUS_SWSH_HOOK_ID = "ability.plus-swsh.post-apply";

    private BuiltinCombatStageHooks() {}

    public static CombatStageHookRegistry registry() {
        return CombatStageHookRegistry.builder()
                // Python registration order is Minus, Plus, Defiant, Competitive, Simple.
                .register(
                        MINUS_SWSH_HOOK_ID,
                        HookSource.ABILITY,
                        CombatStageHookPhase.POST_APPLY,
                        10,
                        BuiltinCombatStageHooks::minusSwshIntensifiesEnemyDrop
                )
                .register(
                        PLUS_SWSH_HOOK_ID,
                        HookSource.ABILITY,
                        CombatStageHookPhase.POST_APPLY,
                        20,
                        BuiltinCombatStageHooks::plusSwshIntensifiesAllyRaise
                )
                .register(
                        "ability.defiant.post-apply",
                        HookSource.ABILITY,
                        CombatStageHookPhase.POST_APPLY,
                        30,
                        BuiltinCombatStageHooks::defiantRaisesAttackAfterExternalDrop
                )
                .register(
                        "ability.competitive.post-apply",
                        HookSource.ABILITY,
                        CombatStageHookPhase.POST_APPLY,
                        40,
                        BuiltinCombatStageHooks::competitiveRaisesSpecialAttackAfterExternalDrop
                )
                .register(
                        "ability.simple.post-apply",
                        HookSource.ABILITY,
                        CombatStageHookPhase.POST_APPLY,
                        50,
                        BuiltinCombatStageHooks::simpleDoublesAppliedStageChange
                )
                .build();
    }

    private static CombatStageHookResult minusSwshIntensifiesEnemyDrop(CombatStageHookContext context) {
        if (context.appliedDelta() >= 0) return CombatStageHookResult.empty();
        if (context.suppresses(MINUS_SWSH_HOOK_ID)) return CombatStageHookResult.empty();
        if (context.attackerId().equals(context.targetId())) return CombatStageHookResult.empty();

        RuntimeCombatantState target = context.target();
        if (target.position() == null) return CombatStageHookResult.empty();
        String targetTeam = context.state().teamId(context.targetId());
        String holder = SpatialAbilityQuery.holdersInRadius(
                        context.state(), target.position(), "Minus [SwSh]", 10
                ).stream()
                .filter(id -> !context.state().teamId(id).equals(targetTeam))
                .findFirst()
                .orElse(null);
        if (holder == null) return CombatStageHookResult.empty();

        CombatStageMutationResult nested = new CombatStageMutationService(context.state(), registry()).apply(
                holder,
                context.targetId(),
                "Minus [SwSh]",
                context.stat(),
                -1,
                "minus_swsh",
                context.options().suppressing(MINUS_SWSH_HOOK_ID)
        );
        ArrayList<BattleEvent> events = new ArrayList<>(nested.events());
        events.add(new RuleEffectEvent(
                "ability",
                "Minus [SwSh]",
                holder,
                context.targetId(),
                context.moveId(),
                "extra_drop",
                -1,
                target.hp()
        ));
        return CombatStageHookResult.events(events);
    }

    private static CombatStageHookResult plusSwshIntensifiesAllyRaise(CombatStageHookContext context) {
        if (context.appliedDelta() <= 0) return CombatStageHookResult.empty();
        if (context.suppresses(PLUS_SWSH_HOOK_ID)) return CombatStageHookResult.empty();

        RuntimeCombatantState target = context.target();
        if (target.position() == null) return CombatStageHookResult.empty();
        String targetTeam = context.state().teamId(context.targetId());
        String holder = SpatialAbilityQuery.holdersInRadius(
                        context.state(), target.position(), "Plus [SwSh]", 10
                ).stream()
                .filter(id -> !id.equals(context.targetId()))
                .filter(id -> context.state().teamId(id).equals(targetTeam))
                .findFirst()
                .orElse(null);
        if (holder == null) return CombatStageHookResult.empty();

        CombatStageMutationResult nested = new CombatStageMutationService(context.state(), registry()).apply(
                holder,
                context.targetId(),
                "Plus [SwSh]",
                context.stat(),
                1,
                "plus_swsh",
                context.options().suppressing(PLUS_SWSH_HOOK_ID)
        );
        ArrayList<BattleEvent> events = new ArrayList<>(nested.events());
        events.add(new RuleEffectEvent(
                "ability",
                "Plus [SwSh]",
                holder,
                context.targetId(),
                context.moveId(),
                "extra_raise",
                1,
                target.hp()
        ));
        return CombatStageHookResult.events(events);
    }

    private static CombatStageHookResult defiantRaisesAttackAfterExternalDrop(CombatStageHookContext context) {
        if (context.appliedDelta() >= 0) return CombatStageHookResult.empty();
        if (normalizedMoveName(context.moveId()).equals("defiant")) return CombatStageHookResult.empty();
        if (context.targetId().equals(context.attackerId())) return CombatStageHookResult.empty();
        if (!context.target().hasAbilityExact("Defiant")) return CombatStageHookResult.empty();

        int bonus = 2 + Math.abs(context.appliedDelta());
        CombatStageMutationResult nested = new CombatStageMutationService(context.state(), registry()).apply(
                context.targetId(),
                context.targetId(),
                "Defiant",
                CombatStat.ATK,
                bonus,
                "defiant"
        );
        return CombatStageHookResult.events(nested.events());
    }

    private static CombatStageHookResult competitiveRaisesSpecialAttackAfterExternalDrop(CombatStageHookContext context) {
        if (context.appliedDelta() >= 0) return CombatStageHookResult.empty();
        if (normalizedMoveName(context.moveId()).equals("competitive")) return CombatStageHookResult.empty();
        if (context.targetId().equals(context.attackerId())) return CombatStageHookResult.empty();
        if (!context.target().hasAbilityExact("Competitive")) return CombatStageHookResult.empty();

        CombatStageMutationResult nested = new CombatStageMutationService(context.state(), registry()).apply(
                context.targetId(),
                context.targetId(),
                "Competitive",
                CombatStat.SPATK,
                2,
                "competitive"
        );
        return CombatStageHookResult.events(nested.events());
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

    private static String normalizedMoveName(String moveId) {
        return moveId == null ? "" : moveId.strip().toLowerCase(Locale.ROOT);
    }
}
