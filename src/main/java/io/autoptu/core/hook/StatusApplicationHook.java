package io.autoptu.core.hook;

@FunctionalInterface
public interface StatusApplicationHook {
    StatusApplicationHookResult resolve(StatusApplicationContext context);
}
