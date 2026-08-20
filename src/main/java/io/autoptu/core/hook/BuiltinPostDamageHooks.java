package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.rules.AbilityIdentityResolution;
import io.autoptu.core.runtime.RuntimeCombatantState;
import io.autoptu.core.runtime.SpatialAbilityQuery;

import java.util.ArrayList;
import java.util.List;

/** Default post-result damage hooks already frozen against the Python oracle. */
public final class BuiltinPostDamageHooks {
    private static final List<SpatialAura> ADJACENT_TYPE_BOOSTS = List.of(
            new SpatialAura("Aqua Boost", "water", 1, 5),
            new SpatialAura("Ignition Boost", "fire", 1, 5),
            new SpatialAura("Thunder Boost", "electric", 1, 5)
    );

    private static final PostDamageHookRegistry STANDARD = PostDamageHookRegistry.builder()
            .register("adaptability-errata", HookSource.ABILITY, 80,
                    context -> RandomD10PostDamageAbility.resolve(
                            context, RandomD10PostDamageAbility.Rule.adaptabilityErrata()))
            .register("damp-errata", HookSource.ABILITY, 90,
                    context -> RandomD10PostDamageAbility.resolve(
                            context, RandomD10PostDamageAbility.Rule.dampErrata()))
            .register("adjacent-elemental-boosts", HookSource.ABILITY, 100,
                    BuiltinPostDamageHooks::adjacentElementalBoosts)
            .register("spatial-general-damage-auras", HookSource.ABILITY, 110,
                    BuiltinPostDamageHooks::spatialGeneralDamageAuras)
            .register("aura-storm", HookSource.ABILITY, 120,
                    BuiltinPostDamageHooks::auraStorm)
            .register("aura-storm-errata", HookSource.ABILITY, 130,
                    BuiltinPostDamageHooks::auraStormErrata)
            .register("analytic", HookSource.ABILITY, 140,
                    BuiltinPostDamageHooks::analytic)
            .build();

    private BuiltinPostDamageHooks() {
    }

    public static PostDamageHookRegistry standardRegistry() {
        return STANDARD;
    }

    private static PostDamageHookResult adjacentElementalBoosts(PostDamageHookContext context) {
        if ("status".equalsIgnoreCase(context.metadata().damageCategory())) {
            return PostDamageHookResult.empty();
        }
        String moveType = normalize(context.metadata().moveType());
        if (moveType.isBlank()) return PostDamageHookResult.empty();

        int bonus = 0;
        ArrayList<BattleEvent> events = new ArrayList<>();
        for (SpatialAura aura : ADJACENT_TYPE_BOOSTS) {
            if (!aura.moveType().equals(moveType)) continue;
            String sourceId = firstAlliedSource(context, aura);
            if (sourceId == null) continue;
            bonus += aura.bonus();
            events.add(damageRuleEvent(context, sourceId, context.actorId(), aura.abilityName(), "damage_bonus", aura.bonus(), context.target().hp()));
        }
        return new PostDamageHookResult(bonus, events);
    }

    private static PostDamageHookResult spatialGeneralDamageAuras(PostDamageHookContext context) {
        if ("status".equalsIgnoreCase(context.metadata().damageCategory())) {
            return PostDamageHookResult.empty();
        }
        String moveType = normalize(context.metadata().moveType());
        if (moveType.isBlank()) return PostDamageHookResult.empty();

        int bonus = 0;
        ArrayList<BattleEvent> events = new ArrayList<>();

        String powerSpotSource = firstAlliedAbilitySource(context, "Power Spot", 2, false, moveType);
        if (powerSpotSource != null) {
            bonus += 5;
            events.add(damageRuleEvent(context, powerSpotSource, context.actorId(), "Power Spot", "damage_bonus", 5, context.target().hp()));
        }

        String typeAuraSource = firstAlliedAbilitySource(context, "Type Aura", 3, true, moveType);
        if (typeAuraSource != null) {
            bonus += 5;
            events.add(damageRuleEvent(context, typeAuraSource, context.actorId(), "Type Aura", "damage_bonus", 5, context.target().hp()));
        }

        return new PostDamageHookResult(bonus, events);
    }

    private static PostDamageHookResult auraStorm(PostDamageHookContext context) {
        if ("status".equalsIgnoreCase(context.metadata().damageCategory())) {
            return PostDamageHookResult.empty();
        }
        boolean hasAuraStorm = AbilityIdentityResolution.matchesExact(context.actor().abilities(), "Aura Storm");
        boolean hasAuraKeyword = context.move().spec().hasKeyword("Aura");
        int injuries = context.state().injuryHistory().currentInjuries(context.actorId());
        List<String> blockers = AuraBreakBlockerQuery.blockers(context.state(), context.actorId());
        AuraStormResolution resolution = AuraStormResolution.normal(
                hasAuraStorm,
                hasAuraKeyword,
                injuries,
                !blockers.isEmpty()
        );
        if (resolution.damageBonus() == 0 || !resolution.emitAuraStormEvent()) {
            return PostDamageHookResult.empty();
        }
        return new PostDamageHookResult(
                resolution.damageBonus(),
                List.of(damageRuleEvent(
                        context,
                        context.actorId(),
                        context.target().combatantId(),
                        "Aura Storm",
                        "damage_bonus",
                        resolution.damageBonus(),
                        context.target().hp()
                ))
        );
    }

    private static PostDamageHookResult auraStormErrata(PostDamageHookContext context) {
        if ("status".equalsIgnoreCase(context.metadata().damageCategory())) {
            return PostDamageHookResult.empty();
        }
        boolean hasAuraStormErrata = AbilityIdentityResolution.matchesExact(
                context.actor().abilities(), "Aura Storm [Errata]");
        int injuries = context.state().injuryHistory().currentInjuries(context.actorId());
        AuraStormResolution base = AuraStormResolution.errata(hasAuraStormErrata, injuries, false);
        if (base.damageBonus() == 0 || !base.emitAuraStormEvent()) {
            return PostDamageHookResult.empty();
        }

        AuraBreakErrataAdjustment adjustment = AuraBreakErrataAdjustment.resolve(
                "Aura Storm [Errata]",
                base.damageBonus(),
                context.state().currentRound(),
                context.actor().temporaryEffects().getAll("aura_break_errata")
        );
        if (adjustment.clearAuraBreakEffects()) {
            context.actor().temporaryEffects().removeAll("aura_break_errata");
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        if (adjustment.emitAuraBreakEvent()) {
            String sourceId = adjustment.sourceId().isBlank() ? context.actorId() : adjustment.sourceId();
            events.add(damageRuleEvent(
                    context,
                    sourceId,
                    context.actorId(),
                    "Aura Break [Errata]",
                    "damage_penalty",
                    -base.damageBonus(),
                    context.actor().hp()
            ));
        }

        int adjustedBonus = adjustment.adjustedBonus();
        events.add(damageRuleEvent(
                context,
                context.actorId(),
                context.target().combatantId(),
                "Aura Storm [Errata]",
                adjustedBonus > 0 ? "damage_bonus" : "damage_penalty",
                adjustedBonus,
                context.target().hp()
        ));
        return new PostDamageHookResult(adjustedBonus, events);
    }

    private static PostDamageHookResult analytic(PostDamageHookContext context) {
        if (!AbilityIdentityResolution.matchesExact(context.actor().abilities(), "Analytic")) {
            return PostDamageHookResult.empty();
        }

        boolean damagingMove = !"status".equalsIgnoreCase(context.metadata().damageCategory());
        boolean defenderHasActionsTaken = !context.target().actionBudget().consumedActions().isEmpty();
        int initiativeIndex = context.state().initiativeProgress().cursor();
        int defenderInitiativeIndex = context.state().initiativeProgress().actorIndex(context.targetId());
        AnalyticResolution resolution = AnalyticResolution.resolve(
                damagingMove,
                defenderHasActionsTaken,
                initiativeIndex,
                defenderInitiativeIndex
        );
        if (resolution.damageBonus() == 0) {
            return PostDamageHookResult.empty();
        }

        AuraBreakErrataAdjustment adjustment = AuraBreakErrataAdjustment.resolve(
                "Analytic",
                resolution.damageBonus(),
                context.state().currentRound(),
                context.actor().temporaryEffects().getAll("aura_break_errata")
        );
        if (adjustment.clearAuraBreakEffects()) {
            context.actor().temporaryEffects().removeAll("aura_break_errata");
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        if (adjustment.emitAuraBreakEvent()) {
            String sourceId = adjustment.sourceId().isBlank() ? context.actorId() : adjustment.sourceId();
            events.add(damageRuleEvent(
                    context,
                    sourceId,
                    context.actorId(),
                    "Aura Break [Errata]",
                    "damage_penalty",
                    -resolution.damageBonus(),
                    context.actor().hp()
            ));
        }

        int adjustedBonus = adjustment.adjustedBonus();
        events.add(damageRuleEvent(
                context,
                context.actorId(),
                context.targetId(),
                "Analytic",
                adjustedBonus > 0 ? "damage_bonus" : "damage_penalty",
                adjustedBonus,
                context.target().hp()
        ));
        return new PostDamageHookResult(adjustedBonus, events);
    }

    private static RuleEffectEvent damageRuleEvent(
            PostDamageHookContext context,
            String sourceId,
            String targetId,
            String abilityName,
            String effect,
            int amount,
            int actorHp
    ) {
        return new RuleEffectEvent(
                "ability",
                abilityName,
                sourceId,
                targetId,
                context.move().moveId(),
                effect,
                amount,
                actorHp
        );
    }

    private static String firstAlliedSource(PostDamageHookContext context, SpatialAura aura) {
        String actorTeam = context.state().teamId(context.actorId());
        for (String candidateId : SpatialAbilityQuery.holdersInRadius(
                context.state(), context.actor().position(), aura.abilityName(), aura.radius())) {
            if (candidateId.equals(context.actorId())) continue;
            RuntimeCombatantState candidate = context.state().requireCombatant(candidateId);
            if (candidate.hp() <= 0 || !context.state().isActive(candidateId)) continue;
            if (!actorTeam.equals(context.state().teamId(candidateId))) continue;
            return candidateId;
        }
        return null;
    }

    private static String firstAlliedAbilitySource(
            PostDamageHookContext context,
            String abilityName,
            int radius,
            boolean requirePrimaryTypeMatch,
            String moveType
    ) {
        String actorTeam = context.state().teamId(context.actorId());
        for (String candidateId : SpatialAbilityQuery.holdersInRadius(
                context.state(), context.actor().position(), abilityName, radius)) {
            RuntimeCombatantState candidate = context.state().requireCombatant(candidateId);
            if (candidate.hp() <= 0 || !context.state().isActive(candidateId)) continue;
            if (!actorTeam.equals(context.state().teamId(candidateId))) continue;
            if (requirePrimaryTypeMatch) {
                String primaryType = candidate.types().isEmpty() ? "" : normalize(candidate.types().getFirst());
                if (primaryType.isBlank() || !primaryType.equals(moveType)) continue;
            }
            return candidateId;
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase();
    }

    private record SpatialAura(String abilityName, String moveType, int radius, int bonus) {}
}
