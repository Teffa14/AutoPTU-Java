package io.autoptu.core.rules;

import java.util.Locale;
import java.util.Set;

/** Core PTU same-type attack bonus (STAB) damage-base resolution. */
public final class StabResolution {
    private StabResolution() {
    }

    public static int resolve(int baseDamageBase, String moveId, String moveType, Set<String> attackerTypes) {
        if (baseDamageBase < 0) {
            throw new IllegalArgumentException("baseDamageBase cannot be negative");
        }
        String normalizedMove = normalizeMoveName(moveId);
        if (normalizedMove.equals("struggle") || normalizedMove.equals("struggle+")) {
            return baseDamageBase;
        }
        String normalizedType = normalizeType(moveType);
        if (normalizedType.isEmpty() || attackerTypes == null || attackerTypes.isEmpty()) {
            return baseDamageBase;
        }
        boolean sameType = attackerTypes.stream()
                .map(StabResolution::normalizeType)
                .anyMatch(normalizedType::equals);
        return baseDamageBase + (sameType ? 2 : 0);
    }

    private static String normalizeType(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeMoveName(String value) {
        if (value == null) return "";
        return value.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
