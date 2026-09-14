package io.autoptu.core.hook;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Pure declarative eligibility contract for reaction families before execution or resource spending. */
public record ReactionEligibilityPolicy(
        boolean ownershipRequired,
        Set<String> blockedStatuses,
        int maxUsesPerRound
) {
    public ReactionEligibilityPolicy {
        if (maxUsesPerRound < 1) {
            throw new IllegalArgumentException("maxUsesPerRound must be positive");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String status : blockedStatuses == null ? Set.<String>of() : blockedStatuses) {
            if (status != null && !status.isBlank()) {
                normalized.add(normalize(status));
            }
        }
        blockedStatuses = Set.copyOf(normalized);
    }

    public static ReactionEligibilityPolicy attackOfOpportunity() {
        return new ReactionEligibilityPolicy(true, Set.of("Sleeping", "Flinched", "Paralyzed"), 1);
    }

    public Eligibility evaluate(Context context) {
        if (context == null) throw new IllegalArgumentException("context is required");
        if (ownershipRequired && !context.ownsReaction()) {
            return new Eligibility(false, Reason.MISSING_OWNERSHIP);
        }
        for (String status : context.statuses()) {
            if (status != null && blockedStatuses.contains(normalize(status))) {
                return new Eligibility(false, Reason.BLOCKED_BY_STATUS);
            }
        }
        if (context.usesThisRound() >= maxUsesPerRound) {
            return new Eligibility(false, Reason.ROUND_USE_EXHAUSTED);
        }
        return new Eligibility(true, Reason.ELIGIBLE);
    }

    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT);
    }

    public record Context(boolean ownsReaction, Set<String> statuses, int usesThisRound) {
        public Context {
            if (usesThisRound < 0) throw new IllegalArgumentException("usesThisRound cannot be negative");
            statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        }
    }

    public record Eligibility(boolean eligible, Reason reason) {}

    public enum Reason {
        ELIGIBLE,
        MISSING_OWNERSHIP,
        BLOCKED_BY_STATUS,
        ROUND_USE_EXHAUSTED
    }
}
