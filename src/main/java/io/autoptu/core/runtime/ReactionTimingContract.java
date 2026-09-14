package io.autoptu.core.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Language-neutral timing metadata for reactions.
 *
 * <p>Interrupt/Priority describe when a reaction may be declared. They are deliberately kept
 * separate from ordinary ActionBudget buckets so adapters and callers cannot invent a second
 * action-economy model.</p>
 */
public record ReactionTimingContract(Timing timing, OptionalInt rank) {
    private static final Pattern INTERRUPT_TRAIT = Pattern.compile("^interrupt[-_ ]?(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIORITY_TRAIT = Pattern.compile("^priority[-_ ]?(\\d+)$", Pattern.CASE_INSENSITIVE);

    public ReactionTimingContract {
        timing = Objects.requireNonNull(timing, "timing");
        rank = Objects.requireNonNull(rank, "rank");
        if (rank.isPresent() && rank.getAsInt() < 0) {
            throw new IllegalArgumentException("rank cannot be negative");
        }
    }

    public static ReactionTimingContract interrupt(int rank) {
        return new ReactionTimingContract(Timing.INTERRUPT, OptionalInt.of(rank));
    }

    public static ReactionTimingContract priority(int rank) {
        return new ReactionTimingContract(Timing.PRIORITY, OptionalInt.of(rank));
    }

    public static ReactionTimingContract ordinary() {
        return new ReactionTimingContract(Timing.ORDINARY, OptionalInt.empty());
    }

    public static ReactionTimingContract fromTrait(String trait) {
        String normalized = Objects.requireNonNull(trait, "trait").trim().toLowerCase(Locale.ROOT);
        Matcher interrupt = INTERRUPT_TRAIT.matcher(normalized);
        if (interrupt.matches()) {
            return interrupt(Integer.parseInt(interrupt.group(1)));
        }
        Matcher priority = PRIORITY_TRAIT.matcher(normalized);
        if (priority.matches()) {
            return priority(Integer.parseInt(priority.group(1)));
        }
        return ordinary();
    }

    public boolean spendsOrdinaryActionBudget() {
        return timing == Timing.ORDINARY;
    }

    public enum Timing {
        ORDINARY,
        INTERRUPT,
        PRIORITY
    }
}
