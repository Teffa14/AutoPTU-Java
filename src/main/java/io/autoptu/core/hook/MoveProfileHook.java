package io.autoptu.core.hook;

@FunctionalInterface
public interface MoveProfileHook {
    MoveProfileHookResult resolve(MoveProfileHookContext context);
}
