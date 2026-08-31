package io.autoptu.core.runtime;

/**
 * Canonical PTU Intercept classification derived from Python-normalized move targeting.
 *
 * <p>The Python oracle treats only normalized target kind {@code melee} as melee Intercept;
 * every other normalized target kind is routed through ranged Intercept. Keeping that collapse
 * in the authoritative core prevents callers from maintaining an independent reaction-kind
 * rules table.</p>
 */
final class InterceptKindResolution {
    private InterceptKindResolution() {
    }

    static String fromNormalizedTargetKind(String normalizedTargetKind) {
        if (normalizedTargetKind == null || normalizedTargetKind.isBlank()) {
            throw new IllegalArgumentException("normalizedTargetKind is required");
        }
        return "melee".equals(normalizedTargetKind.strip().toLowerCase(java.util.Locale.ROOT))
                ? "melee"
                : "ranged";
    }

    static boolean isMelee(String normalizedTargetKind) {
        return "melee".equals(fromNormalizedTargetKind(normalizedTargetKind));
    }
}
