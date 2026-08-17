package io.autoptu.core.model;

/**
 * BattleState-independent movement capabilities for one combatant.
 *
 * Abilities, statuses, trainer features, weather, and equipment should be resolved
 * into this profile before movement geometry is evaluated.
 */
public record MovementProfile(
        GridCoord position,
        int overland,
        int swimSpeed,
        int skySpeed,
        double sprintMultiplier,
        boolean canFly,
        boolean canSwim,
        boolean canBurrow,
        boolean canPhase,
        boolean liquefied,
        boolean ignoresRoughTerrain,
        int wallrunnerLimit
) {
    public MovementProfile {
        if (position == null) {
            throw new IllegalArgumentException("position is required");
        }
        overland = Math.max(0, overland);
        swimSpeed = Math.max(0, swimSpeed);
        skySpeed = Math.max(0, skySpeed);
        sprintMultiplier = sprintMultiplier <= 0 ? 1.0 : sprintMultiplier;
        wallrunnerLimit = Math.max(0, wallrunnerLimit);
    }

    public static MovementProfile walking(GridCoord position, int overland) {
        return new MovementProfile(
                position,
                overland,
                0,
                0,
                1.0,
                false,
                false,
                false,
                false,
                false,
                false,
                0
        );
    }

    /** Preserve resolved capabilities while advancing the authoritative grid position. */
    public MovementProfile withPosition(GridCoord nextPosition) {
        return new MovementProfile(
                nextPosition,
                overland,
                swimSpeed,
                skySpeed,
                sprintMultiplier,
                canFly,
                canSwim,
                canBurrow,
                canPhase,
                liquefied,
                ignoresRoughTerrain,
                wallrunnerLimit
        );
    }
}
