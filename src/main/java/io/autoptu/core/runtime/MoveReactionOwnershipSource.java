package io.autoptu.core.runtime;

import io.autoptu.core.action.MoveOption;

import java.util.Locale;
import java.util.Objects;

/** Reaction ownership derived from the combatant's canonical moveset. */
public final class MoveReactionOwnershipSource implements ReactionOwnershipSource {
    public static final String SOURCE_ID = "move";

    @Override
    public String sourceId() {
        return SOURCE_ID;
    }

    @Override
    public Resolution resolve(BattleRuntimeState battleState, String combatantId, String reactionKey) {
        Objects.requireNonNull(battleState, "battleState");
        String canonicalCombatantId = requireText(combatantId, "combatantId");
        String canonicalReactionKey = normalizeKey(requireText(reactionKey, "reactionKey"));
        battleState.requireCombatant(canonicalCombatantId);

        if (!battleState.hasCanonicalMoves(canonicalCombatantId)) {
            return Resolution.UNKNOWN;
        }

        for (MoveOption move : battleState.moveOptions(canonicalCombatantId)) {
            if (normalizeKey(move.moveId()).equals(canonicalReactionKey)) {
                return Resolution.OWNED;
            }
        }
        return Resolution.NOT_OWNED;
    }

    static String normalizeKey(String value) {
        String normalized = requireText(value, "reactionKey").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("reactionKey is required");
        return normalized;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.strip();
    }
}
