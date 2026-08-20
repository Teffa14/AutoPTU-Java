package io.autoptu.core.hook;

/** Resolves authoritative effects that modify damage after the base/type pipeline. */
@FunctionalInterface
public interface PostDamageHook {
    PostDamageHookResult resolve(PostDamageHookContext context);
}
