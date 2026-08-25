package io.autoptu.core.hook;

import io.autoptu.core.runtime.TemporaryEffectEntry;
import io.autoptu.core.runtime.TemporaryEffectStore;

/** Python-parity resolver for BattleState._roll_penalty used by move-special effect rolls. */
public final class MoveSpecialRollPenaltyResolution {
    private MoveSpecialRollPenaltyResolution() {}

    public static int resolve(TemporaryEffectStore effects, int currentRound) {
        if (effects == null) throw new IllegalArgumentException("effects is required");
        if (currentRound < 0) throw new IllegalArgumentException("currentRound cannot be negative");

        int penalty = 0;
        for (TemporaryEffectEntry entry : effects.getAll("all_roll_penalty")) {
            Integer expiresRound = optionalStrictInt(entry.payload().get("expires_round"));
            if (expiresRound != null && currentRound > expiresRound) {
                effects.removeEntry(entry);
                continue;
            }
            Integer amount = forgivingInt(entry.payload().get("amount"));
            if (amount != null) penalty += amount;
        }
        return Math.max(0, penalty);
    }

    private static Integer optionalStrictInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value).strip());
    }

    private static Integer forgivingInt(Object value) {
        if (value == null) return 0;
        try {
            if (value instanceof Number number) return number.intValue();
            return Integer.parseInt(String.valueOf(value).strip());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
