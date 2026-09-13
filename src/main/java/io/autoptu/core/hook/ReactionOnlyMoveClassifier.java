package io.autoptu.core.hook;

import java.util.Locale;

/**
 * Python-oracle-compatible classifier for moves that are offered only from reaction windows.
 *
 * <p>The pinned Python oracle classifies a move as reaction-only when its activation is
 * {@code interrupt} or {@code reaction}, or when the combined local/canonical range/effect text
 * contains {@code trigger:} or {@code reaction}. This class intentionally performs classification
 * only; it does not decide timing, legality, resource consumption, or execution.</p>
 */
public final class ReactionOnlyMoveClassifier {
    private ReactionOnlyMoveClassifier() {}

    public static boolean isReactionOnly(
            String activation,
            String rangeText,
            String effectsText,
            String canonicalRangeText,
            String canonicalEffectsText
    ) {
        String normalizedActivation = normalize(activation);
        if (normalizedActivation.equals("interrupt") || normalizedActivation.equals("reaction")) {
            return true;
        }

        String text = String.join(" ",
                nullToEmpty(rangeText),
                nullToEmpty(effectsText),
                nullToEmpty(canonicalRangeText),
                nullToEmpty(canonicalEffectsText)
        ).strip().toLowerCase(Locale.ROOT);

        return text.contains("trigger:") || text.contains("reaction");
    }

    private static String normalize(String value) {
        return nullToEmpty(value).strip().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
