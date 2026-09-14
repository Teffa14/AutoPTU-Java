package io.autoptu.core.runtime;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed timing metadata imported from content keywords such as {@code interrupt-1}
 * and {@code priority-20}.
 *
 * <p>This contract preserves the imported family and rank only. It deliberately does
 * not assign PTU action-budget cost or semantic execution timing. The pinned Python
 * oracle contains Priority-labelled content whose effect text executes as an Interrupt,
 * so callers must resolve semantic timing through authoritative rule/content contracts
 * rather than inferring it from this metadata alone.</p>
 */
public record ImportedTimingKeyword(Family family, int rank) {
    private static final Pattern TIMING_KEYWORD = Pattern.compile(
            "^(interrupt|priority)[-_ ]?(\\d+)$",
            Pattern.CASE_INSENSITIVE
    );

    public ImportedTimingKeyword {
        family = Objects.requireNonNull(family, "family");
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive");
        }
    }

    public static Optional<ImportedTimingKeyword> parse(String keyword) {
        String normalized = Objects.requireNonNull(keyword, "keyword")
                .trim()
                .toLowerCase(Locale.ROOT);
        Matcher matcher = TIMING_KEYWORD.matcher(normalized);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Family family = switch (matcher.group(1)) {
            case "interrupt" -> Family.INTERRUPT;
            case "priority" -> Family.PRIORITY;
            default -> throw new IllegalStateException("unreachable timing family");
        };
        return Optional.of(new ImportedTimingKeyword(family, Integer.parseInt(matcher.group(2))));
    }

    public enum Family {
        INTERRUPT,
        PRIORITY
    }
}
