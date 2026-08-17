package io.autoptu.core.model;

/**
 * BattleState-independent jump capabilities.
 *
 * Trainer features such as Acrobat/Traveler must already be resolved into the
 * long/high jump limits. Wallrunner remains explicit because Python movement.py
 * uses it both to extend long-jump reach and to cross blocked path steps.
 */
public record JumpProfile(
        GridCoord position,
        int longJump,
        int highJump,
        boolean canFly,
        boolean canSwim,
        boolean canBurrow,
        boolean canPhase,
        boolean liquefied,
        int wallrunnerLimit
) {
    public JumpProfile {
        if (position == null) {
            throw new IllegalArgumentException("position is required");
        }
        longJump = Math.max(0, longJump);
        highJump = Math.max(0, highJump);
        wallrunnerLimit = Math.max(0, wallrunnerLimit);
    }

    public static JumpProfile basic(GridCoord position, int longJump, int highJump) {
        return new JumpProfile(
                position,
                longJump,
                highJump,
                false,
                false,
                false,
                false,
                false,
                0
        );
    }
}
