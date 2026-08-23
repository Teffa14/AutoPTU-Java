package io.autoptu.core.rules;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure contract for target-owned abilities that block status application.
 *
 * <p>The caller must supply whether defensive abilities are currently suppressed. That flag remains
 * separate until Neutralizing Gas / ignore-defensive-abilities state is fully owned by the runtime.</p>
 */
public final class StatusAbilityPreventionResolution {
    private static final List<Rule> RULES = List.of(
            new Rule("Own Tempo", Set.of("confused", "confusion")),
            new Rule("Oblivious", Set.of("enraged", "infatuated")),
            new Rule("Run Away", Set.of("slowed", "stuck", "trapped")),
            new Rule("Inner Focus", Set.of("flinch", "flinched")),
            new Rule("Immunity", Set.of("poison", "poisoned", "badly poisoned")),
            new Rule("Insomnia", Set.of("sleep", "asleep")),
            new Rule("Vital Spirit", Set.of("sleep", "asleep"))
    );

    private StatusAbilityPreventionResolution() {
    }

    public static Optional<String> blockingAbility(
            Collection<String> abilities,
            String status,
            boolean abilitiesSuppressed
    ) {
        Objects.requireNonNull(abilities, "abilities");
        if (abilitiesSuppressed) return Optional.empty();
        String normalizedStatus = normalize(status);
        if (normalizedStatus.isEmpty()) return Optional.empty();

        for (Rule rule : RULES) {
            if (rule.statuses().contains(normalizedStatus)
                    && AbilityIdentityResolution.matchesRegistration(abilities, rule.ability())) {
                return Optional.of(rule.ability());
            }
        }
        return Optional.empty();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private record Rule(String ability, Set<String> statuses) {
    }
}
