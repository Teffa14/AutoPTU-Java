package io.autoptu.core.hook;

import io.autoptu.core.event.BattleEvent;
import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.rules.AbilityIdentityResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Declarative family for post-result abilities that add one d10 of final damage.
 *
 * Python resolves these rolls after ordinary hit/damage arithmetic. The caller must bind
 * the authoritative battle RNG to the post-damage context; clients and Minecraft adapters
 * never supply the roll or resulting bonus.
 */
public final class RandomD10PostDamageAbility {
    private RandomD10PostDamageAbility() {
    }

    public record Rule(String abilityName, String requiredMoveType, boolean requireStab) {
        public Rule {
            if (abilityName == null || abilityName.isBlank()) {
                throw new IllegalArgumentException("abilityName is required");
            }
            requiredMoveType = normalize(requiredMoveType);
        }

        public static Rule adaptabilityErrata() {
            return new Rule("Adaptability [Errata]", "", true);
        }

        public static Rule dampErrata() {
            return new Rule("Damp [Errata]", "water", false);
        }
    }

    public static PostDamageHookResult resolve(PostDamageHookContext context, Rule rule) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (rule == null) throw new IllegalArgumentException("rule is required");
        if (!AbilityIdentityResolution.matchesExact(context.actor().abilities(), rule.abilityName())) {
            return PostDamageHookResult.empty();
        }
        if ("status".equalsIgnoreCase(context.metadata().damageCategory())) {
            return PostDamageHookResult.empty();
        }

        String moveType = normalize(context.metadata().moveType());
        if (moveType.isBlank()) return PostDamageHookResult.empty();
        if (!rule.requiredMoveType().isBlank() && !rule.requiredMoveType().equals(moveType)) {
            return PostDamageHookResult.empty();
        }
        if (rule.requireStab() && context.actor().types().stream()
                .map(RandomD10PostDamageAbility::normalize)
                .noneMatch(moveType::equals)) {
            return PostDamageHookResult.empty();
        }

        int baseBonus = context.requireRng().randIntInclusive(1, 10);
        AuraBreakErrataAdjustment adjustment = AuraBreakErrataAdjustment.resolve(
                rule.abilityName(),
                baseBonus,
                context.state().currentRound(),
                context.actor().temporaryEffects().getAll("aura_break_errata")
        );
        if (adjustment.clearAuraBreakEffects()) {
            context.actor().temporaryEffects().removeAll("aura_break_errata");
        }

        ArrayList<BattleEvent> events = new ArrayList<>();
        if (adjustment.emitAuraBreakEvent()) {
            String sourceId = adjustment.sourceId().isBlank() ? context.actorId() : adjustment.sourceId();
            events.add(event(
                    context,
                    sourceId,
                    context.actorId(),
                    "Aura Break [Errata]",
                    "damage_penalty",
                    -baseBonus,
                    context.actor().hp()
            ));
        }

        int bonus = adjustment.adjustedBonus();
        if (bonus != 0) {
            events.add(event(
                    context,
                    context.actorId(),
                    context.targetId(),
                    rule.abilityName(),
                    bonus > 0 ? "damage_bonus" : "damage_penalty",
                    bonus,
                    context.target().hp()
            ));
        }
        return bonus == 0 && events.isEmpty()
                ? PostDamageHookResult.empty()
                : new PostDamageHookResult(bonus, List.copyOf(events));
    }

    private static RuleEffectEvent event(
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

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
