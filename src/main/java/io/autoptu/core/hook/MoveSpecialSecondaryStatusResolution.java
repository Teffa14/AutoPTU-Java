package io.autoptu.core.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the generic text-driven move-special status family used by the pinned Python oracle.
 *
 * <p>This contract decides which status applications a move special requests from canonical
 * effects text and an already-resolved effect roll. It deliberately does not apply the status;
 * application/prevention remains owned by the battle runtime.</p>
 */
public final class MoveSpecialSecondaryStatusResolution {
    private static final String VERBS =
            "burns?|burned|burnt|poisons?|poisoned|paralyzes|paralyzed|freezes|frozen|"
                    + "confuses|confused|flinches|flinched";

    private static final Pattern STATUS_THRESHOLD = Pattern.compile(
            "\\b(?<verb>" + VERBS + ")\\b.*?\\bon\\s+(?:a\\s+)?(?<threshold>\\d+)\\+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATUS_EVEN = Pattern.compile(
            "\\b(?<verb>" + VERBS + ")\\b.*?\\beven-numbered roll",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern STATUS_ALWAYS = Pattern.compile(
            "\\b(?<verb>" + VERBS + ")\\b(?:\\s+the\\s+target|\\s+its\\s+target)?\\b",
            Pattern.CASE_INSENSITIVE
    );

    private MoveSpecialSecondaryStatusResolution() {}

    public static List<StatusRequest> resolve(String effectsText, int effectRoll) {
        String text = normalizeEffectsText(effectsText).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return List.of();

        ArrayList<StatusRequest> requests = new ArrayList<>();
        Matcher threshold = STATUS_THRESHOLD.matcher(text);
        boolean hasThresholdPattern = threshold.find();
        if (hasThresholdPattern && effectRoll >= Integer.parseInt(threshold.group("threshold"))) {
            addForVerb(requests, threshold.group("verb"));
        }

        if (!hasThresholdPattern) {
            Matcher always = STATUS_ALWAYS.matcher(text);
            if (always.find() && !text.contains("on ")) {
                addForVerb(requests, always.group("verb"));
            }

            Matcher even = STATUS_EVEN.matcher(text);
            if (even.find() && effectRoll % 2 == 0) {
                addForVerb(requests, even.group("verb"));
            }
        }

        if (text.contains("falls asleep")) {
            requests.add(new StatusRequest("Sleep", "sleep", null));
        }
        return List.copyOf(requests);
    }

    private static void addForVerb(List<StatusRequest> requests, String rawVerb) {
        String verb = rawVerb == null ? "" : rawVerb.toLowerCase(Locale.ROOT);
        if (verb.startsWith("burn")) {
            requests.add(new StatusRequest("Burned", "burn", null));
        } else if (verb.startsWith("poison")) {
            requests.add(new StatusRequest("Poisoned", "poison", null));
        } else if (verb.startsWith("paralyzes")) {
            requests.add(new StatusRequest("Paralyzed", "paralysis", null));
        } else if (verb.startsWith("freeze")) {
            requests.add(new StatusRequest("Frozen", "freeze", null));
        } else if (verb.startsWith("confuse")) {
            requests.add(new StatusRequest("Confused", "confusion", null));
        } else if (verb.startsWith("flinch")) {
            requests.add(new StatusRequest("Flinched", "flinch", 1));
        }
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

    public record StatusRequest(String status, String effect, Integer remaining) {
        public StatusRequest {
            if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
            if (effect == null || effect.isBlank()) throw new IllegalArgumentException("effect is required");
        }
    }
}
