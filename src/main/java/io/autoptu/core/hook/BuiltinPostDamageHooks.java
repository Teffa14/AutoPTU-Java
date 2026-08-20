package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.DamageResult;
import io.autoptu.core.runtime.SpatialAbilityQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Parity-backed effects that modify the final damage result after ordinary damage arithmetic. */
public final class BuiltinPostDamageHooks {
    private static final int AURA_BONUS = 5;
    private static final List<AuraRule> ADJACENT_AURAS = List.of(
            new AuraRule("Aqua Boost", "water", 1, true),
            new AuraRule("Ignition Boost", "fire", 1, true),
            new AuraRule("Thunder Boost", "electric", 1, true),
            new AuraRule("Power Spot", null, 2, false)
    );
    private static final PostDamageHookRegistry STANDARD = PostDamageHookRegistry.builder()
            .register("adjacent-spatial-damage-auras", HookSource.ABILITY, 300,
                    BuiltinPostDamageHooks::adjacentSpatialAuras)
            .build();

    private BuiltinPostDamageHooks() {
    }

    public static PostDamageHookRegistry standardRegistry() {
        return STANDARD;
    }

    private static PostDamageHookResult adjacentSpatialAuras(PostDamageHookContext context) {
        String moveType = context.metadata().moveType() == null
                ? ""
                : context.metadata().moveType().strip().toLowerCase(Locale.ROOT);
        DamageResult damage = context.damage();
        ArrayList<BattleEvent> events = new ArrayList<>();

        for (AuraRule rule : ADJACENT_AURAS) {
            if (rule.moveType() != null && !rule.moveType().equals(moveType)) continue;
            String sourceId = firstSameTeamHolder(context, rule);
            if (sourceId == null) continue;
            damage = withFinalDamage(damage, damage.damage() + AURA_BONUS);
            events.add(new RuleEffectEvent(
                    "ability",
                    rule.ability(),
                    sourceId,
                    context.actorId(),
                    context.move().moveId(),
                    "damage_bonus",
                    AURA_BONUS,
                    context.target().hp()
            ));
        }
        return PostDamageHookResult.of(damage, events);
    }

    private static String firstSameTeamHolder(PostDamageHookContext context, AuraRule rule) {
        String actorTeam = context.state().teamId(context.actorId());
        for (String candidateId : SpatialAbilityQuery.holdersInRadius(
                context.state(), context.actor().position(), rule.ability(), rule.radius())) {
            if (rule.excludeActor() && candidateId.equals(context.actorId())) continue;
            if (!context.state().teamId(candidateId).equals(actorTeam)) continue;
            return candidateId;
        }
        return null;
    }

    private static DamageResult withFinalDamage(DamageResult original, int finalDamage) {
        return new DamageResult(
                original.dice(),
                original.baseRoll(),
                original.criticalExtraRoll(),
                original.damageRoll(),
                original.preModifierDamage(),
                original.preTypeDamage(),
                Math.max(0, finalDamage)
        );
    }

    private record AuraRule(String ability, String moveType, int radius, boolean excludeActor) {
    }
}
