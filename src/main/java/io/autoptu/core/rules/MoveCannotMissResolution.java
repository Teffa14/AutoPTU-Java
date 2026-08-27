package io.autoptu.core.rules;

import io.autoptu.core.model.MoveSpec;

import java.util.Locale;
import java.util.Set;

/** Python BattleState._move_cannot_miss parity for reusable attack-attempt gates. */
public final class MoveCannotMissResolution {
    private static final Set<String> ALWAYS_HIT_MOVES = Set.of(
            "false surrender",
            "feint attack",
            "future sight"
    );

    private MoveCannotMissResolution() {}

    public static boolean resolve(String moveName, MoveSpec move) {
        if (move == null) return false;
        String normalizedName = moveName == null ? "" : moveName.strip().toLowerCase(Locale.ROOT);
        if (ALWAYS_HIT_MOVES.contains(normalizedName)) return true;
        if (move.hasKeyword("cannot miss") || move.hasKeyword("never miss")) return true;
        String text = move.effectsText().toLowerCase(Locale.ROOT);
        return text.contains("cannot miss") || text.contains("never misses") || text.contains("always hits");
    }
}
