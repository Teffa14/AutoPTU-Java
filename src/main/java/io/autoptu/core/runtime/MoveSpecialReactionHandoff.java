package io.autoptu.core.runtime;

import io.autoptu.core.hook.PreDamageReactionResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Preserves Python's shared mutable move-result state across battle-pipeline stages.
 *
 * <p>Move-special PRE_DAMAGE handlers and the later POST_DAMAGE phase observe the same result
 * dictionary in Python. Defender reactions and later pre-HP adjustments update hit, damage, and
 * type multiplier in between; unrelated keys, including the original crit snapshot, remain
 * untouched unless that stage explicitly owns them.</p>
 */
public final class MoveSpecialReactionHandoff {
    private MoveSpecialReactionHandoff() {}

    public static Map<String, Object> apply(
            Map<String, ?> sharedResult,
            PreDamageReactionResult reaction
    ) {
        Objects.requireNonNull(reaction, "reaction");
        return apply(sharedResult, reaction.hit(), reaction.damage(), reaction.typeMultiplier());
    }

    public static Map<String, Object> apply(
            Map<String, ?> sharedResult,
            boolean hit,
            int damage,
            double typeMultiplier
    ) {
        LinkedHashMap<String, Object> next = new LinkedHashMap<>();
        if (sharedResult != null) sharedResult.forEach(next::put);
        next.put("hit", hit);
        next.put("damage", Math.max(0, damage));
        next.put("type_multiplier", typeMultiplier);
        return Collections.unmodifiableMap(next);
    }
}
