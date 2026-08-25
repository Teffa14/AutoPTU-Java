package io.autoptu.core.runtime;

import io.autoptu.core.hook.PreDamageReactionResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Preserves Python's shared mutable move-result state across defender PRE-damage reactions.
 *
 * <p>Move-special PRE_DAMAGE handlers and the later POST_DAMAGE phase observe the same result
 * dictionary in Python. Defender reactions update hit, damage, and type multiplier in between;
 * unrelated keys, including the original crit snapshot, remain untouched unless that reaction
 * explicitly owns them. This helper makes that handoff explicit before the live POST_DAMAGE
 * runtime wiring is enabled.</p>
 */
public final class MoveSpecialReactionHandoff {
    private MoveSpecialReactionHandoff() {}

    public static Map<String, Object> apply(
            Map<String, ?> sharedResult,
            PreDamageReactionResult reaction
    ) {
        Objects.requireNonNull(reaction, "reaction");

        LinkedHashMap<String, Object> next = new LinkedHashMap<>();
        if (sharedResult != null) sharedResult.forEach(next::put);
        next.put("hit", reaction.hit());
        next.put("damage", reaction.damage());
        next.put("type_multiplier", reaction.typeMultiplier());
        return Collections.unmodifiableMap(next);
    }
}
