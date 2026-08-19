package io.autoptu.core.rules;

import java.util.List;
import java.util.Locale;

/**
 * Python-parity matching for ability hook registration names.
 *
 * A combatant carrying an `[Errata]` variant still satisfies the corresponding
 * base ability registration, while an Errata-specific registration remains exact.
 * This lets the ordered registry reproduce Python behavior where both the base
 * hook and the Errata hook may run for the same canonical ability identity.
 */
public final class AbilityIdentityResolution {
    private static final String ERRATA_SUFFIX = " [errata]";

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

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }
}
