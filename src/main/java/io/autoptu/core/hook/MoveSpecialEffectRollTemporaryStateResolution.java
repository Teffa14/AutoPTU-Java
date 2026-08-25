package io.autoptu.core.hook;

import io.autoptu.core.runtime.TemporaryEffectEntry;
import io.autoptu.core.runtime.TemporaryEffectStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Python-parity state transition for temporary effects read by move-special _effect_roll. */
public final class MoveSpecialEffectRollTemporaryStateResolution {
    private MoveSpecialEffectRollTemporaryStateResolution() {}

    public static Result resolve(
            TemporaryEffectStore attackerEffects,
            TemporaryEffectStore defenderEffects,
            String moveName,
            int currentRound
    ) {
        if (attackerEffects == null) throw new IllegalArgumentException("attackerEffects is required");
        if (currentRound < 0) throw new IllegalArgumentException("currentRound cannot be negative");
        String canonicalMove = normalize(moveName);

        if (defenderEffects != null) {
            for (TemporaryEffectEntry entry : defenderEffects.getAll("immutable_mind_block")) {
                Integer expiresRound = optionalInt(entry, "expires_round");
                if (expiresRound != null && currentRound > expiresRound) {
                    defenderEffects.removeEntry(entry);
                    continue;
                }
                String entryMove = normalize(entry.payload().get("move"));
                if (!entryMove.isEmpty() && !entryMove.equals(canonicalMove)) continue;
                return new Result(Block.IMMUTABLE_MIND, List.of());
            }
        }

        for (TemporaryEffectEntry entry : attackerEffects.getAll("effect_range_block")) {
            Integer expiresRound = optionalInt(entry, "expires_round");
            if (expiresRound != null && currentRound > expiresRound) {
                attackerEffects.removeEntry(entry);
                continue;
            }
            return new Result(Block.EFFECT_RANGE, List.of());
        }

        ArrayList<Integer> bonuses = new ArrayList<>();
        for (TemporaryEffectEntry entry : attackerEffects.getAll("effect_range_bonus")) {
            Integer expiresRound = optionalInt(entry, "expires_round");
            if (expiresRound != null && currentRound > expiresRound) {
                attackerEffects.removeEntry(entry);
                continue;
            }
            Integer amount = forgivingInt(entry.payload().get("amount"));
            if (amount != null) bonuses.add(amount);
        }
        return new Result(Block.NONE, List.copyOf(bonuses));
    }

    private static Integer optionalInt(TemporaryEffectEntry entry, String key) {
        Object value = entry.payload().get(key);
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

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }

    public enum Block { NONE, IMMUTABLE_MIND, EFFECT_RANGE }

    public record Result(Block block, List<Integer> effectRangeBonuses) {
        public Result {
            if (block == null) throw new IllegalArgumentException("block is required");
            effectRangeBonuses = List.copyOf(effectRangeBonuses == null ? List.of() : effectRangeBonuses);
        }
    }
}
