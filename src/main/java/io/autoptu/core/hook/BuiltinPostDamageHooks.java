package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
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
            .register("adjacent-elemental-boosts", HookSource.ABILITY, 100,
                    BuiltinPostDamageHooks::adjacentElementalBoosts)
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
            events.add(new RuleEffectEvent(
                    "ability",
                    aura.abilityName(),
                    sourceId,
                    context.actorId(),
                    context.move().moveId(),
                    "damage_bonus",
                    aura.bonus(),
                    context.target().hp()
            ));
        }
        return new PostDamageHookResult(bonus, events);
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

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase();
    }

    private record SpatialAura(String abilityName, String moveType, int radius, int bonus) {}
}
