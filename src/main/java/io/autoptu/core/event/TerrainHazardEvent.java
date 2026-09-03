package io.autoptu.core.event;

import io.autoptu.core.model.GridCoord;

import java.util.Set;

/**
 * Public semantic event for an authoritative terrain or hazard interaction.
 *
 * <p>The core supplies the complete resolved payload. Adapters render this event without
 * re-evaluating trap ownership, Naturewalk, terrain matching, status application, or consumption.</p>
 */
public record TerrainHazardEvent(
        String effect,
        String actorId,
        String trapKey,
        String trapName,
        String sourceId,
        String description,
        int targetHp,
        GridCoord coordinate,
        Set<String> terrains
) implements BattleEvent {
    public TerrainHazardEvent {
        effect = safe(effect);
        actorId = safe(actorId);
        trapKey = safe(trapKey);
        trapName = safe(trapName);
        sourceId = safe(sourceId);
        description = safe(description);
        terrains = Set.copyOf(terrains == null ? Set.of() : terrains);
        if (effect.isBlank()) throw new IllegalArgumentException("effect is required");
        if (actorId.isBlank()) throw new IllegalArgumentException("actorId is required");
        if (trapKey.isBlank()) throw new IllegalArgumentException("trapKey is required");
        if (targetHp < 0) throw new IllegalArgumentException("targetHp cannot be negative");
    }

    public static TerrainHazardEvent trapBlock(
            String actorId,
            String trapKey,
            String description,
            int targetHp
    ) {
        return new TerrainHazardEvent(
                "trap_block", actorId, trapKey, "", "", description, targetHp, null, Set.of()
        );
    }

    public static TerrainHazardEvent trigger(
            String actorId,
            String trapKey,
            String trapName,
            String sourceId,
            String description,
            int targetHp,
            GridCoord coordinate,
            Set<String> terrains
    ) {
        if (coordinate == null) throw new IllegalArgumentException("trigger coordinate is required");
        return new TerrainHazardEvent(
                "trigger", actorId, trapKey, trapName, sourceId, description,
                targetHp, coordinate, terrains
        );
    }

    @Override
    public BattleEventKind kind() {
        return BattleEventKind.TERRAIN_HAZARD;
    }

    @Override
    public String stableKey() {
        String coord = coordinate == null ? "" : coordinate.x() + "," + coordinate.y();
        return String.join("|",
                kind().value(), effect, actorId, trapKey, trapName, sourceId,
                description, Integer.toString(targetHp), coord,
                terrains.stream().sorted().reduce((left, right) -> left + "," + right).orElse("")
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value.strip();
    }
}
