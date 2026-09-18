package io.autoptu.core.runtime;

import io.autoptu.core.model.GridCoord;

import java.util.Objects;

/**
 * Minimal language-neutral combatant geometry captured from one authoritative battle snapshot.
 *
 * <p>The anchor and PTU size label are sufficient for footprint-aware adjacency through the
 * shared targeting contract. Minecraft/Cobblemon adapters may populate this DTO, but they do
 * not decide adjacency or reaction legality.</p>
 */
public record AuthoritativeCombatantGeometry(
        String combatantId,
        GridCoord anchor,
        String sizeLabel
) {
    public AuthoritativeCombatantGeometry {
        combatantId = combatantId == null ? "" : combatantId.strip();
        if (combatantId.isBlank()) {
            throw new IllegalArgumentException("combatantId is required");
        }
        Objects.requireNonNull(anchor, "anchor");
        sizeLabel = sizeLabel == null || sizeLabel.isBlank() ? "Medium" : sizeLabel.strip();
    }
}
