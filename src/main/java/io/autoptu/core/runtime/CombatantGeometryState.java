package io.autoptu.core.runtime;

/**
 * Server-owned spatial metadata for a combatant that is not part of movement speed.
 *
 * Minecraft/Cobblemon model scale is presentation data. PTU footprint size used for
 * range, collision, and targeting must come from this canonical battle snapshot.
 */
public record CombatantGeometryState(String sizeLabel) {
    public static final CombatantGeometryState MEDIUM = new CombatantGeometryState("Medium");

    public CombatantGeometryState {
        sizeLabel = sizeLabel == null || sizeLabel.isBlank() ? "Medium" : sizeLabel.strip();
    }
}
