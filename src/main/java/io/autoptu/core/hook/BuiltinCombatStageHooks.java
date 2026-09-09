package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.CombatStageChangedEvent;
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

        CombatStageMutationResult nested = CombatStageMutationService.authoritative(context.state()).apply(
                holder,
                context.targetId(),
                "Minus [SwSh]",
                context.stat(),
                -1,
                "minus_swsh",
                context.options().suppressing(MINUS_SWSH_HOOK_ID)
        );
        ArrayList<BattleEvent> events = new ArrayList<>(committedNestedStageEvents(
                context,
                nested,
                holder,
                context.targetId(),
                "Minus [SwSh]",
                context.stat(),
                "minus_swsh",
                "Minus [SwSh] intensifies the stat drop."
        ));
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

        CombatStageMutationResult nested = CombatStageMutationService.authoritative(context.state()).apply(
                holder,
                context.targetId(),
                "Plus [SwSh]",
                context.stat(),
                1,
                "plus_swsh",
                context.options().suppressing(PLUS_SWSH_HOOK_ID)
        );
        ArrayList<BattleEvent> events = new ArrayList<>(committedNestedStageEvents(
                context,
                nested,
                holder,
                context.targetId(),
                "Plus [SwSh]",
                context.stat(),
                "plus_swsh",
                "Plus [SwSh] intensifies the stat raise."
        ));
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
        CombatStageMutationResult nested = CombatStageMutationService.authoritative(context.state()).apply(
                context.targetId(),
                context.targetId(),
                "Defiant",
                CombatStat.ATK,
                bonus,
                "defiant"
        );
        return CombatStageHookResult.events(committedNestedStageEvents(
                context,
                nested,
                context.targetId(),
                context.targetId(),
                "Defiant",
                CombatStat.ATK,
                "defiant",
                "Defiant raises Attack by +2 CS."
        ));
    }

    private static CombatStageHookResult competitiveRaisesSpecialAttackAfterExternalDrop(CombatStageHookContext context) {
        if (context.appliedDelta() >= 0) return CombatStageHookResult.empty();
        if (normalizedMoveName(context.moveId()).equals("competitive")) return CombatStageHookResult.empty();
        if (context.targetId().equals(context.attackerId())) return CombatStageHookResult.empty();
        if (!context.target().hasAbilityExact("Competitive")) return CombatStageHookResult.empty();

        CombatStageMutationResult nested = CombatStageMutationService.authoritative(context.state()).apply(
                context.targetId(),
                context.targetId(),
                "Competitive",
                CombatStat.SPATK,
                2,
                "competitive"
        );
        return CombatStageHookResult.events(committedNestedStageEvents(
                context,
                nested,
                context.targetId(),
                context.targetId(),
                "Competitive",
                CombatStat.SPATK,
                "competitive",
                "Competitive raises Special Attack by +2 CS."
        ));
    }

    private static List<BattleEvent> committedNestedStageEvents(
            CombatStageHookContext context,
            CombatStageMutationResult nested,
            String actorId,
            String targetId,
            String moveId,
            CombatStat stat,
            String effect,
            String description
    ) {
        ArrayList<BattleEvent> events = new ArrayList<>();
        if (nested.baseAppliedDelta() != 0) {
            RuntimeCombatantState target = context.state().requireCombatant(targetId);
            events.add(new CombatStageChangedEvent(
                    actorId,
                    targetId,
                    moveId,
                    io.autoptu.core.model.CombatStageStat.fromCombatStat(stat),
                    effect,
                    Math.abs(nested.baseAppliedDelta()),
                    nested.baseStage(),
                    description,
                    target.hp(),
                    context.state().currentRound(),
                    ""
            ));
        }
        events.addAll(nested.events());
        return List.copyOf(events);
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
