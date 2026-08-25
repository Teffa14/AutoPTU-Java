package io.autoptu.core.hook;

import io.autoptu.core.model.MoveSpec;

/**
 * Python-parity resolver for the effects text consumed by generic move-special rules.
 *
 * <p>Canonical move content should normally materialize {@link MoveSpec#effectsText()} directly.
 * The fallback argument represents a server-owned canonical move lookup for legacy/incomplete
 * move specs; Minecraft/Cobblemon must not supply rule text while battle resolution is running.</p>
 */
public final class MoveEffectsTextResolution {
    private MoveEffectsTextResolution() {}

    public static String resolve(MoveSpec move, String canonicalFallbackEffectsText) {
        if (move == null) throw new IllegalArgumentException("move is required");
        if (!move.effectsText().isEmpty()) return move.effectsText();
        return canonicalFallbackEffectsText == null ? "" : canonicalFallbackEffectsText;
    }
}
