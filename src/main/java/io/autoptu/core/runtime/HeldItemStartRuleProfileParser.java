package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the subset of canonical held-item description text consumed by the generic START profile.
 *
 * Python's parse_item_effects() intentionally recognizes a much broader surface. This parser is
 * bounded to the reusable START families already represented by HeldItemStartRuleProfile; other
 * item mechanics remain outside this contract until their authoritative runtime boundary exists.
 */
public final class HeldItemStartRuleProfileParser {
    private HeldItemStartRuleProfileParser() {}

    private static final Pattern BASE_BY_SIGNED = Pattern.compile("base\\s+([a-z. ]+?)\\s+by\\s+([+-]\\d+)");
    private static final Pattern IMPROVE_PAIR = Pattern.compile(
            "improves?\\s+(?:your\\s+)?base\\s+([a-z. ]+?)\\s+by\\s+(\\d+)\\s*(?:,|and)\\s*base\\s+([a-z. ]+?)\\s+by\\s+(\\d+)");
    private static final Pattern GENERIC_BASE_INCREASE = Pattern.compile(
            "(?:improves?|increases?|raises?)\\s+(?:your\\s+)?base\\s+([a-z. ]+?)\\s+by\\s+(\\d+)");
    private static final Pattern BASE_STAT_BY = Pattern.compile(
            "(?:your\\s+)?(attack|defense|special attack|special defense|sp\\. atk\\.?|sp\\. def\\.?|spatk|spdef|speed|spd)\\s+base\\s+stat\\s+by\\s+(\\d+)");
    private static final Pattern BASE_DECREASE = Pattern.compile(
            "(?:reduces?|lowers?)\\s+(?:your\\s+)?base\\s+([a-z. ]+?)\\s+by\\s+(\\d+)");
    private static final Pattern DEF_SPDEF_SCALAR = Pattern.compile("base\\s+def\\s+and\\s+spdef\\s+are\\s+increased\\s+by\\s+(\\d+)%");
    private static final Pattern ATK_SPATK_SCALAR = Pattern.compile("base\\s+atk\\s+and\\s+sp(?:atk|\\. atk\\.?)\\s+are\\s+increased\\s+by\\s+(\\d+)%");
    private static final Pattern BASE_SCALAR = Pattern.compile("base\\s+([a-z. ]+?)\\s+is\\s+increased\\s+by\\s+(\\d+)%");
    private static final Pattern TYPE_POWER_ACCURACY = Pattern.compile(
            "increases?\\s+the\\s+power\\s+and\\s+accuracy\\s+of\\s+\\[?([a-z]+)\\]?\\s+attacks?\\s+by\\s+(\\d+)%");
    private static final Pattern ACCURACY_PERCENT = Pattern.compile("accuracy of the user'?s? attacks is increased by\\s+\\+?(\\d+)%");
    private static final Pattern ACCURACY_ROLLS = Pattern.compile("grants?\\s+\\+?(\\d+)\\s+bonus to all accuracy rolls");
    private static final Pattern ACCURACY_LABEL = Pattern.compile("\\baccuracy\\s*\\+(\\d+)");
    private static final Pattern ACCURACY_FLAT = Pattern.compile("gains?\\s+\\+?(\\d+)\\s+accuracy\\b");
    private static final Pattern ACCURACY_LOWER_AV = Pattern.compile(
            "gains?\\s+\\+?(\\d+)\\s+accuracy on attacks targeting creatures with a lower");
    private static final Pattern EVASION_SPEED = Pattern.compile("speed\\s+evasion\\s*\\+(\\d+)");
    private static final Pattern EVASION_ALL = Pattern.compile("all stat evasions?\\s*\\+(\\d+)");
    private static final Pattern EVASION_GENERIC = Pattern.compile("\\bevasion\\s*\\+(\\d+)");

    public static HeldItemStartRuleProfile parse(String description) {
        String text = description == null ? "" : description.strip().toLowerCase(Locale.ROOT);
        List<HeldItemStartTemporaryEffectResolution.StatAmount> changes = parseBaseStatChanges(text);
        List<HeldItemStartTemporaryEffectResolution.StatScalar> scalars = parseBaseStatScalars(text);

        HeldItemStartTemporaryEffectResolution.TypeAmount typedAccuracy = null;
        Matcher typePowerAccuracy = TYPE_POWER_ACCURACY.matcher(text);
        if (typePowerAccuracy.find()) {
            String type = title(typePowerAccuracy.group(1));
            int percent = Integer.parseInt(typePowerAccuracy.group(2));
            typedAccuracy = new HeldItemStartTemporaryEffectResolution.TypeAmount(type, Math.max(1, pythonRound(percent / 5.0)));
        }

        Integer accuracy = null;
        Matcher match = ACCURACY_PERCENT.matcher(text);
        if (match.find()) {
            accuracy = Math.max(1, pythonRound(Integer.parseInt(match.group(1)) / 5.0));
        }
        if (accuracy == null && (match = ACCURACY_ROLLS.matcher(text)).find()) {
            accuracy = Integer.parseInt(match.group(1));
        }
        if (accuracy == null && (match = ACCURACY_LABEL.matcher(text)).find()) {
            accuracy = Integer.parseInt(match.group(1));
        }
        if (accuracy == null && (match = ACCURACY_FLAT.matcher(text)).find()) {
            accuracy = Math.max(1, pythonRound(Integer.parseInt(match.group(1)) / 5.0));
        }

        Integer lowerAv = null;
        match = ACCURACY_LOWER_AV.matcher(text);
        if (match.find()) {
            lowerAv = Math.max(1, pythonRound(Integer.parseInt(match.group(1)) / 5.0));
        }

        Integer statusEvasion = null;
        Integer allEvasion = null;
        Matcher speed = EVASION_SPEED.matcher(text);
        Matcher all = EVASION_ALL.matcher(text);
        Matcher generic = EVASION_GENERIC.matcher(text);
        if (speed.find()) statusEvasion = Integer.parseInt(speed.group(1));
        if (all.find()) allEvasion = Integer.parseInt(all.group(1));
        if (allEvasion == null && statusEvasion == null && generic.find()) {
            allEvasion = Integer.parseInt(generic.group(1));
        }

        Integer initiative = text.contains("adds +10 to their initiative") ? 10 : null;
        Double speedScalar = text.contains("speed stat is halved") ? 0.5 : null;

        return new HeldItemStartRuleProfile(
                changes,
                scalars,
                accuracy,
                lowerAv,
                typedAccuracy,
                statusEvasion,
                allEvasion,
                initiative,
                speedScalar
        );
    }

    private static List<HeldItemStartTemporaryEffectResolution.StatAmount> parseBaseStatChanges(String text) {
        ArrayList<HeldItemStartTemporaryEffectResolution.StatAmount> values = new ArrayList<>();
        addSignedMatches(values, BASE_BY_SIGNED.matcher(text));

        Matcher pair = IMPROVE_PAIR.matcher(text);
        if (pair.find()) {
            values.add(new HeldItemStartTemporaryEffectResolution.StatAmount(stat(pair.group(1)), Integer.parseInt(pair.group(2))));
            values.add(new HeldItemStartTemporaryEffectResolution.StatAmount(stat(pair.group(3)), Integer.parseInt(pair.group(4))));
        }

        Matcher generic = GENERIC_BASE_INCREASE.matcher(text);
        while (generic.find()) {
            String raw = generic.group(1).strip();
            int amount = Integer.parseInt(generic.group(2));
            List<String> split = raw.contains(" and ") && raw.contains("special defense")
                    ? List.of(raw.split(" and ")) : List.of(raw);
            for (String part : split) values.add(new HeldItemStartTemporaryEffectResolution.StatAmount(stat(part), amount));
        }

        Matcher baseStat = BASE_STAT_BY.matcher(text);
        while (baseStat.find()) {
            values.add(new HeldItemStartTemporaryEffectResolution.StatAmount(stat(baseStat.group(1)), Integer.parseInt(baseStat.group(2))));
        }

        Matcher decrease = BASE_DECREASE.matcher(text);
        while (decrease.find()) {
            values.add(new HeldItemStartTemporaryEffectResolution.StatAmount(stat(decrease.group(1)), -Integer.parseInt(decrease.group(2))));
        }
        return dedupe(values);
    }

    private static void addSignedMatches(List<HeldItemStartTemporaryEffectResolution.StatAmount> values, Matcher matcher) {
        while (matcher.find()) {
            values.add(new HeldItemStartTemporaryEffectResolution.StatAmount(stat(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        }
    }

    private static List<HeldItemStartTemporaryEffectResolution.StatScalar> parseBaseStatScalars(String text) {
        ArrayList<HeldItemStartTemporaryEffectResolution.StatScalar> values = new ArrayList<>();
        Matcher pair = DEF_SPDEF_SCALAR.matcher(text);
        if (pair.find()) {
            double multiplier = 1 + Integer.parseInt(pair.group(1)) / 100.0;
            values.add(new HeldItemStartTemporaryEffectResolution.StatScalar("def", multiplier));
            values.add(new HeldItemStartTemporaryEffectResolution.StatScalar("spdef", multiplier));
        }
        pair = ATK_SPATK_SCALAR.matcher(text);
        if (pair.find()) {
            double multiplier = 1 + Integer.parseInt(pair.group(1)) / 100.0;
            values.add(new HeldItemStartTemporaryEffectResolution.StatScalar("atk", multiplier));
            values.add(new HeldItemStartTemporaryEffectResolution.StatScalar("spatk", multiplier));
        }
        Matcher one = BASE_SCALAR.matcher(text);
        if (one.find()) {
            values.add(new HeldItemStartTemporaryEffectResolution.StatScalar(
                    stat(one.group(1)), 1 + Integer.parseInt(one.group(2)) / 100.0));
        }
        return dedupe(values);
    }

    private static String stat(String raw) {
        String value = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT)
                .replace(" stats", "").replace(" stat", "").strip();
        return switch (value) {
            case "atk", "attack" -> "atk";
            case "def", "defense" -> "def";
            case "spatk", "sp. atk", "sp. atk.", "special attack" -> "spatk";
            case "spdef", "sp. def", "sp. def.", "special defense" -> "spdef";
            case "spd", "speed" -> "spd";
            default -> value.replace(" ", "");
        };
    }

    private static String title(String value) {
        if (value == null || value.isEmpty()) return value;
        String lower = value.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static int pythonRound(double value) {
        return (int) Math.rint(value);
    }

    private static <T> List<T> dedupe(List<T> values) {
        Set<T> seen = new LinkedHashSet<>(values);
        return List.copyOf(seen);
    }
}
