package io.autoptu.core.hook;

import io.autoptu.core.event.RuleEffectEvent;
import io.autoptu.core.model.AttackModifier;
import io.autoptu.core.model.ModifierTiming;
import io.autoptu.core.rules.BuiltinDamageModifierResolution;
import io.autoptu.core.runtime.HeldItemState;

import java.util.List;

/** Default authoritative damage hooks that are already parity-backed. */
public final class BuiltinDamageModifierHooks {
    private static final DamageModifierHookRegistry STANDARD = DamageModifierHookRegistry.builder()
            .register(
                    "burned-physical-scalar",
                    HookSource.STATUS,
                    100,
                    context -> DamageModifierHookResult.modifiersOnly(
                            BuiltinDamageModifierResolution.resolve(
                                    context.metadata().damageCategory(),
                                    context.actorStatuses()
                            )
                    )
            )
            .register(
                    "pink-pearl-psychic-flat",
                    HookSource.ITEM,
                    200,
                    BuiltinDamageModifierHooks::pinkPearlPsychicDamage
            )
            .build();

    private BuiltinDamageModifierHooks() {
    }

    public static DamageModifierHookRegistry standardRegistry() {
        return STANDARD;
    }

    private static DamageModifierHookResult pinkPearlPsychicDamage(DamageModifierHookContext context) {
        if ("status".equalsIgnoreCase(context.metadata().damageCategory())) {
            return DamageModifierHookResult.empty();
        }
        if (!"psychic".equalsIgnoreCase(context.metadata().moveType())) {
            return DamageModifierHookResult.empty();
        }
        HeldItemState pinkPearl = context.actorHeldItems().stream()
                .filter(item -> "pink pearl".equals(item.normalizedName()))
                .findFirst()
                .orElse(null);
        if (pinkPearl == null) {
            return DamageModifierHookResult.empty();
        }

        AttackModifier modifier = new AttackModifier(
                "item-pink-pearl-flat",
                "damage_flat",
                5,
                ModifierTiming.PRE_DAMAGE,
                pinkPearl.name()
        );
        RuleEffectEvent event = new RuleEffectEvent(
                "item",
                pinkPearl.name(),
                context.actorId(),
                context.targetId(),
                context.move().moveId(),
                "damage_flat",
                5,
                context.actor().hp()
        );
        return DamageModifierHookResult.of(List.of(modifier), List.of(event));
    }
}
