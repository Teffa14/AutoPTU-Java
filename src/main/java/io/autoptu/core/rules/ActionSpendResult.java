package io.autoptu.core.rules;

import java.util.Optional;

/**
 * Describes which action-economy resource paid for one authoritative action spend.
 *
 * <p>This is semantic metadata only. It does not decide legality. {@link ActionBudget}
 * remains the single owner of resource availability and mutation.</p>
 */
public record ActionSpendResult(boolean consumed, Source source, String grantName) {
    public enum Source {
        FREE,
        BASE,
        EXTRA,
        STANDARD_CONVERSION,
        UNAVAILABLE
    }

    public ActionSpendResult {
        if (source == null) {
            throw new IllegalArgumentException("source is required");
        }
        if (source == Source.EXTRA) {
            if (grantName == null || grantName.isBlank()) {
                throw new IllegalArgumentException("extra spend requires grantName");
            }
        } else if (grantName != null) {
            throw new IllegalArgumentException("grantName is only valid for EXTRA spends");
        }
        if (consumed == (source == Source.UNAVAILABLE)) {
            throw new IllegalArgumentException("consumed must match spend source");
        }
    }

    public Optional<String> extraGrantName() {
        return Optional.ofNullable(grantName);
    }

    public static ActionSpendResult free() {
        return new ActionSpendResult(true, Source.FREE, null);
    }

    public static ActionSpendResult base() {
        return new ActionSpendResult(true, Source.BASE, null);
    }

    public static ActionSpendResult extra(String grantName) {
        return new ActionSpendResult(true, Source.EXTRA, grantName);
    }

    public static ActionSpendResult standardConversion() {
        return new ActionSpendResult(true, Source.STANDARD_CONVERSION, null);
    }

    public static ActionSpendResult unavailable() {
        return new ActionSpendResult(false, Source.UNAVAILABLE, null);
    }
}
