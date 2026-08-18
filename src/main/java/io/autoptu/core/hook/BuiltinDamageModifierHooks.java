package io.autoptu.core.hook;

import io.autoptu.core.rules.BuiltinDamageModifierResolution;

/** Default authoritative damage hooks that are already parity-backed. */
public final class BuiltinDamageModifierHooks {
    private static final DamageModifierHookRegistry STANDARD = DamageModifierHookRegistry.builder()
            .register(
                    "burned-physical-scalar",
                    HookSource.STATUS,
                    100,
                    context -> BuiltinDamageModifierResolution.resolve(
                            context.metadata().damageCategory(),
                            context.actorStatuses()
                    )
            )
            .build();

    private BuiltinDamageModifierHooks() {
    }

    public static DamageModifierHookRegistry standardRegistry() {
        return STANDARD;
    }
}
