package io.autoptu.core.hook;

/** One pure authoritative pre-damage transformation of effective move metadata. */
@FunctionalInterface
public interface EffectiveMoveHook {
    EffectiveMoveHookResult resolve(EffectiveMoveHookContext context);
}
