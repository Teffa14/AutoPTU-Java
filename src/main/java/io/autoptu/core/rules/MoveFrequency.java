package io.autoptu.core.rules;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** PTU move-frequency parsing mirrored from Python AutoPTU. */
public final class MoveFrequency {
    private static final Pattern SCENE = Pattern.compile("scene(?:\\s*x\\s*(\\d+))?");
    private static final Pattern DAILY = Pattern.compile("daily(?:\\s*x\\s*(\\d+))?");

    private MoveFrequency() {
    }

    public enum Scope {
        BATTLE,
        ROUND
    }

    public record Definition(String slug, int limit, Scope scope, String raw) {
        public Definition {
            if (slug == null || slug.isBlank()) throw new IllegalArgumentException("slug is required");
            if (limit < 1) throw new IllegalArgumentException("limit must be positive");
            if (scope == null) throw new IllegalArgumentException("scope is required");
            raw = raw == null ? "" : raw;
        }
    }

    public static Optional<Definition> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String token = raw.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        Matcher scene = SCENE.matcher(token);
        if (scene.matches()) {
            return Optional.of(new Definition("scene", parseLimit(scene.group(1)), Scope.BATTLE, raw));
        }

        Matcher daily = DAILY.matcher(token);
        if (daily.matches()) {
            return Optional.of(new Definition("daily", parseLimit(daily.group(1)), Scope.BATTLE, raw));
        }

        if (token.equals("eot")) {
            return Optional.of(new Definition("eot", 1, Scope.ROUND, raw));
        }
        return Optional.empty();
    }

    public static boolean available(String raw, int battleUsage, int roundUsage) {
        Optional<Definition> parsed = parse(raw);
        if (parsed.isEmpty()) return true;
        Definition definition = parsed.get();
        int usage = definition.scope() == Scope.ROUND ? roundUsage : battleUsage;
        return usage < definition.limit();
    }

    private static int parseLimit(String value) {
        return value == null ? 1 : Integer.parseInt(value);
    }
}
