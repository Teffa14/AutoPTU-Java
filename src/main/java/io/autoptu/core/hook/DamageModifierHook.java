package io.autoptu.core.hook;

/** One authoritative rule contribution to the damage modifier pipeline. */
@FunctionalInterface
public interface DamageModifierHook {
    DamageModifierHookResult resolve(DamageModifierHookContext context);
}
