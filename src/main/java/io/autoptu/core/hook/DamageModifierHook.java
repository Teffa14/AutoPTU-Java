package io.autoptu.core.hook;

import io.autoptu.core.model.AttackModifier;

import java.util.List;

/** One authoritative rule contribution to the damage modifier pipeline. */
@FunctionalInterface
public interface DamageModifierHook {
    List<AttackModifier> resolve(DamageModifierHookContext context);
}
