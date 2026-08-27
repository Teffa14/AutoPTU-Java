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

    /**
     * Backwards-compatible fail-closed identity for legacy snapshots that lack canonical
     * Pokemon profile content. Internal combatant ids are not Pokemon names.
     */
    public static CombatantProfileIdentity fromCombatantId(String combatantId) {
        return new CombatantProfileIdentity("", "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
