package io.autoptu.core.rules;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Python-parity matching for ability hook registration names.
 *
 * A combatant carrying an `[Errata]` variant still satisfies the corresponding
 * base ability registration, while an Errata-specific registration remains exact.
 * Exact matching is exposed separately for Python call sites using
 * ability_variants.has_ability_exact(), where an Errata variant must not silently
 * satisfy the base name. The pinned oracle has a small explicit equivalence family
 * for Huge Power / Pure Power Errata names, reproduced here declaratively.
 */
public final class AbilityIdentityResolution {
    private static final String ERRATA_SUFFIX = " [errata]";
    private static final Map<String, Set<String>> EXACT_EQUIVALENTS = Map.of(
            "huge power / pure power [errata]", Set.of(
                    "huge power / pure power [errata]",
                    "huge power [errata]",
                    "pure power [errata]"
            ),
            "huge power [errata]", Set.of(
                    "huge power [errata]",
                    "huge power / pure power [errata]"
            ),
            "pure power [errata]", Set.of(
                    "pure power [errata]",
                    "huge power / pure power [errata]"
            )
    );

    private AbilityIdentityResolution() {
    }

    public static boolean matchesRegistration(List<String> abilities, String registrationName) {
        String target = normalize(registrationName);
        if (target.isBlank() || abilities == null || abilities.isEmpty()) return false;
        boolean registrationIsErrata = target.endsWith(ERRATA_SUFFIX);
        for (String ability : abilities) {
            String candidate = normalize(ability);
            if (candidate.equals(target)) return true;
            if (!registrationIsErrata && candidate.equals(target + ERRATA_SUFFIX)) return true;
        }
        return false;
    }

    /** Python ability_variants.has_ability_exact() semantics. */
    public static boolean matchesExact(List<String> abilities, String abilityName) {
        String target = normalize(abilityName);
        if (target.isBlank() || abilities == null || abilities.isEmpty()) return false;
        for (String ability : abilities) {
            String candidate = normalize(ability);
            if (candidate.equals(target)) return true;
            Set<String> equivalents = EXACT_EQUIVALENTS.get(candidate);
            if (equivalents != null && equivalents.contains(target)) return true;
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}