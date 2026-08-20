package io.autoptu.core.hook;

@FunctionalInterface
public interface PostDamageHook {
    PostDamageHookResult resolve(PostDamageHookContext context);
}
