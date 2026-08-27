package io.autoptu.core.runtime;

/**
 * Immutable server-owned identity used by rules that match Pokemon name/species.
 *
 * <p>Python Chronicler profile matching reads target.spec.name and target.spec.species.
 * Minecraft/Cobblemon may render these labels, but battle rules must read the canonical
 * snapshot materialized by the core.</p>
 */
public record CombatantProfileIdentity(String name, String species) {
    public CombatantProfileIdentity {
        name = normalize(name);
        species = normalize(species);
    }

    /** Backwards-compatible identity for legacy snapshots that only carry a combatant id. */
    public static CombatantProfileIdentity fromCombatantId(String combatantId) {
        return new CombatantProfileIdentity(combatantId, "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
