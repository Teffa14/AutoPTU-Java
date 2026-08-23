package io.autoptu.core.rules;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parity contract for Python move_traits.forced_movement_instruction(). */
public final class ForcedMovementInstructionResolution {
    private static final Pattern PUSH_DISTANCE = Pattern.compile("push\\D*(\\d+)");
    private static final Pattern PULL_DISTANCE = Pattern.compile("pull\\D*(\\d+)");

    private ForcedMovementInstructionResolution() {}

    public static Optional<ForcedMovementInstruction> resolve(
            Collection<String> keywords,
            String effectsText
    ) {
        String description = effectsText == null ? "" : effectsText.toLowerCase(Locale.ROOT);
        Collection<String> safeKeywords = keywords == null ? java.util.List.of() : keywords;

        Optional<ForcedMovementInstruction> push = resolveKind(
                ForcedMovementInstruction.Kind.PUSH,
                "push",
                PUSH_DISTANCE,
                safeKeywords,
                description
        );
        if (push.isPresent()) return push;
        return resolveKind(
                ForcedMovementInstruction.Kind.PULL,
                "pull",
                PULL_DISTANCE,
                safeKeywords,
                description
        );
    }

    private static Optional<ForcedMovementInstruction> resolveKind(
            ForcedMovementInstruction.Kind kind,
            String token,
            Pattern distancePattern,
            Collection<String> keywords,
            String description
    ) {
        boolean keywordMatch = false;
        for (String keyword : keywords) {
            if (keyword != null && keyword.toLowerCase(Locale.ROOT).equals(token)) {
                keywordMatch = true;
                break;
            }
        }
        if (!keywordMatch && !description.contains(token)) return Optional.empty();

        int distance = 1;
        Matcher matcher = distancePattern.matcher(description);
        if (matcher.find()) {
            try {
                distance = Math.max(1, Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                distance = 1;
            }
        }
        return Optional.of(new ForcedMovementInstruction(kind, distance));
    }
}
