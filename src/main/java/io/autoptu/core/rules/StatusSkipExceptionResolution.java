package io.autoptu.core.rules;

import java.util.Locale;
import java.util.Set;

/**
 * Pure resolution of the Python StatusController exceptions that allow a combatant
 * to act through an otherwise pending status skip.
 *
 * Minecraft/Fabric must never decide these exceptions. A future adapter may map
 * trusted Trainer Feature and temporary-effect state into these inputs, while the
 * battle core decides whether the pending skip remains in force.
 */
public final class StatusSkipExceptionResolution {
    private static final Set<String> SUPREME_CONCENTRATION_STATUSES = Set.of(
            "paralyzed", "flinch", "flinched", "enraged", "rage", "confusion", "confused"
    );
    private static final Set<String> DUELISTS_MANUAL_STATUSES = Set.of(
            "confusion", "confused", "enraged", "rage", "infatuated", "suppressed"
    );

    private StatusSkipExceptionResolution() {}

    public static Result resolve(
            String status,
            String signatureModification,
            String signatureMove,
            boolean duelistsManualIgnoreStatus
    ) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedSignature = normalizeSignatureModification(signatureModification);

        if (normalizedSignature.equals("supremeconcentration")
                && SUPREME_CONCENTRATION_STATUSES.contains(normalizedStatus)) {
            return new Result(false, ExceptionKind.SUPREME_CONCENTRATION, safe(signatureMove));
        }
        if (duelistsManualIgnoreStatus && DUELISTS_MANUAL_STATUSES.contains(normalizedStatus)) {
            return new Result(false, ExceptionKind.DUELISTS_MANUAL, "");
        }
        return new Result(true, ExceptionKind.NONE, "");
    }

    static String normalizeSignatureModification(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String lower = value.strip().toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(ch);
            }
        }
        return normalized.toString();
    }

    private static String normalizeStatus(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }

    public enum ExceptionKind {
        NONE,
        SUPREME_CONCENTRATION,
        DUELISTS_MANUAL
    }

    public record Result(boolean skipTurn, ExceptionKind exceptionKind, String signatureMove) {
        public Result {
            if (exceptionKind == null) {
                throw new IllegalArgumentException("exceptionKind is required");
            }
            signatureMove = signatureMove == null ? "" : signatureMove;
        }

        public String stableKey() {
            return skipTurn + "|" + exceptionKind.name().toLowerCase(Locale.ROOT) + "|" + signatureMove;
        }
    }
}
