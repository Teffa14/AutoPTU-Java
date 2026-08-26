package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the generic text-driven Combat Stage secondary-effect family used by the pinned Python oracle.
 *
 * <p>This contract only produces ordered stage-change requests. Mutation, prevention, reflection and
 * reaction handling remain owned by the authoritative combat-stage runtime.</p>
 */
public final class MoveSpecialSecondaryCombatStageResolution {
    private static final Pattern RAISE = Pattern.compile(
            "raise(?:s)?\\s+the\\s+(?<target>user|target)'?s?\\s+"
                    + "(?<stats>[\\w\\s/]+?)\\s+(?:by\\s+)?\\+?(?<amount>\\d+)\\s+(?:combat stage|cs)"
                    + "(?:\\s+on\\s+(?<threshold>\\d+)\\+)?(?:\\s+each)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LOWER = Pattern.compile(
            "lower(?:s)?\\s+the\\s+(?<target>user|target)'?s?\\s+"
                    + "(?<stats>[\\w\\s/]+?)\\s+(?:by\\s+)?-?(?<amount>\\d+)\\s+(?:combat stage|cs)"
                    + "(?:\\s+on\\s+(?<threshold>\\d+)\\+)?(?:\\s+each)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALT_TARGET_LOWER = Pattern.compile(
            "target'?s?\\s+(?<stats>[\\w\\s/]+?)\\s+is\\s+lowered\\s+(?:by\\s+)?-?(?<amount>\\d+)\\s+(?:combat stage|cs)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ALT_RAISE = Pattern.compile(
            "raise(?:s)?\\s+the\\s+user'?s?\\s+(?<stats>[\\w\\s/]+?)\\s+(?<amount>\\d+)\\s+(?:combat stage|cs)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SIMPLE_LOWER = Pattern.compile(
            "all legal targets.*?(?:are|have their)?\\s*(?<stat>accuracy|evasion|attack|defense|special attack|special defense|speed)\\s+"
                    + "(?:is\\s+)?lowered\\s+(?:by\\s+)?-?(?<amount>\\d+)\\s+(?:combat stage|cs)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Map<String, String> STAT_ALIASES = Map.ofEntries(
            Map.entry("special defense", "spdef"),
            Map.entry("special attack", "spatk"),
            Map.entry("special def", "spdef"),
            Map.entry("special atk", "spatk"),
            Map.entry("sp. def", "spdef"),
            Map.entry("sp. atk", "spatk"),
            Map.entry("sp def", "spdef"),
            Map.entry("sp atk", "spatk"),
            Map.entry("accuracy", "accuracy"),
            Map.entry("evasion", "evasion"),
            Map.entry("defense", "def"),
            Map.entry("attack", "atk"),
            Map.entry("speed", "spd"),
            Map.entry("spdef", "spdef"),
            Map.entry("spatk", "spatk"),
            Map.entry("def", "def"),
            Map.entry("atk", "atk"),
            Map.entry("spd", "spd")
    );

    private MoveSpecialSecondaryCombatStageResolution() {}

    public static List<StageRequest> resolve(String effectsText, int effectRoll) {
        String text = normalizeEffectsText(effectsText).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return List.of();

        ArrayList<StageRequest> requests = new ArrayList<>();
        for (PatternAndSign entry : List.of(new PatternAndSign(RAISE, 1), new PatternAndSign(LOWER, -1))) {
            Matcher match = entry.pattern().matcher(text);
            if (!match.find()) continue;
            String threshold = match.group("threshold");
            if (threshold != null && effectRoll < Integer.parseInt(threshold)) continue;
            int delta = Integer.parseInt(match.group("amount")) * entry.sign();
            TargetRole target = "user".equals(match.group("target").toLowerCase(Locale.ROOT))
                    ? TargetRole.USER : TargetRole.TARGET;
            for (String stat : normalizeStats(match.group("stats"))) {
                requests.add(new StageRequest(target, stat, delta));
            }
        }

        Matcher altTargetLower = ALT_TARGET_LOWER.matcher(text);
        if (altTargetLower.find()) {
            int delta = -Integer.parseInt(altTargetLower.group("amount"));
            for (String stat : normalizeStats(altTargetLower.group("stats"))) {
                requests.add(new StageRequest(TargetRole.TARGET, stat, delta));
            }
        }

        Matcher altRaise = ALT_RAISE.matcher(text);
        if (altRaise.find()) {
            int delta = Integer.parseInt(altRaise.group("amount"));
            for (String stat : normalizeStats(altRaise.group("stats"))) {
                requests.add(new StageRequest(TargetRole.USER, stat, delta));
            }
        }

        Matcher simpleLower = SIMPLE_LOWER.matcher(text);
        if (simpleLower.find()) {
            List<String> stats = normalizeStats(simpleLower.group("stat"));
            if (!stats.isEmpty()) {
                requests.add(new StageRequest(
                        TargetRole.TARGET,
                        stats.getFirst(),
                        -Integer.parseInt(simpleLower.group("amount"))
                ));
            }
        }
        return List.copyOf(requests);
    }

    private static List<String> normalizeStats(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        ArrayList<Map.Entry<String, String>> aliases = new ArrayList<>(STAT_ALIASES.entrySet());
        aliases.sort((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()));
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String part : raw.toLowerCase(Locale.ROOT).split("and|,|/")) {
            String trimmed = part.trim();
            for (Map.Entry<String, String> alias : aliases) {
                if (trimmed.contains(alias.getKey())) {
                    normalized.add(alias.getValue());
                    break;
                }
            }
        }
        return List.copyOf(normalized);
    }

    private static String normalizeEffectsText(String text) {
        if (text == null || text.isEmpty()) return "";
        return text
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u00a0', ' ');
    }

    public enum TargetRole {
        USER,
        TARGET
    }

    public record StageRequest(TargetRole target, String stat, int delta) {
        public StageRequest {
            if (target == null) throw new IllegalArgumentException("target is required");
            if (stat == null || stat.isBlank()) throw new IllegalArgumentException("stat is required");
        }
    }

    private record PatternAndSign(Pattern pattern, int sign) {}
}
