package io.autoptu.core.rules;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure contract for target-owned abilities that block status application in the pinned Python
 * status-application boundary.
 *
 * <p>The caller supplies whether target abilities are currently suppressed. Other status immunity
 * families that Python resolves in different subsystems must not be inferred here.</p>
 */
public final class StatusAbilityPreventionResolution {
    private static final List<Rule> RULES = List.of(
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

        List<String> abilityList = List.copyOf(abilities);
        for (Rule rule : RULES) {
            if (rule.statuses().contains(normalizedStatus)
                    && AbilityIdentityResolution.matchesRegistration(abilityList, rule.ability())) {
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
