package io.autoptu.core.hook;

@FunctionalInterface
public interface PreResolutionTargetHook {
    PreResolutionTargetResult resolve(PreResolutionTargetContext context, PreResolutionTargetResult current);
}
