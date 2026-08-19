package io.autoptu.core.runtime;

import java.util.Locale;

/** Stable server-authoritative identity for one combatant ability. */
public record AbilityState(String abilityId, String name) {
    public AbilityState {
        if (abilityId == null || abilityId.isBlank()) {
            throw new IllegalArgumentException("abilityId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ability name is required");
        }
        abilityId = abilityId.strip();
        name = name.strip();
    }

    public String normalizedName() {
        return name.toLowerCase(Locale.ROOT);
    }
}
