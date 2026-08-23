package io.autoptu.core.hook;

@FunctionalInterface
public interface PreDamageReactionHook {
    PreDamageReactionResult resolve(PreDamageReactionContext context, PreDamageReactionResult current);
}
