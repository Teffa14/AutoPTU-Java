package io.autoptu.core.hook;

@FunctionalInterface
public interface LifecycleHook {
    LifecycleHookResult apply(LifecycleHookContext context);
}
