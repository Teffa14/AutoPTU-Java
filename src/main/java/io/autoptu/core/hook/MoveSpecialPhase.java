package io.autoptu.core.hook;

import java.util.Locale;

/** Python-compatible move-special dispatch phases. */
public enum MoveSpecialPhase {
    PRE_DAMAGE,
    POST_DAMAGE,
    END_ACTION;

    public static MoveSpecialPhase fromPythonPhase(String raw) {
        String normalized = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "pre_damage" -> PRE_DAMAGE;
            case "end_action" -> END_ACTION;
            case "post_damage" -> POST_DAMAGE;
            default -> POST_DAMAGE;
        };
    }
}