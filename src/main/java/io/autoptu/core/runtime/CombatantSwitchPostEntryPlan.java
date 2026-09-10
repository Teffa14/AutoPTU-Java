package io.autoptu.core.runtime;

import java.util.List;

/**
 * Ordered server-authoritative families that follow the initial combatant-entry ability boundary.
 *
 * <p>This contract intentionally names behavior families rather than concrete implementation
 * methods. It gives the switch runtime a stable staging seam for reusable ability and Trainer
 * Feature registries while Python remains authoritative.</p>
 */
public record CombatantSwitchPostEntryPlan(List<Stage> stages) {
    public CombatantSwitchPostEntryPlan {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }

    public static CombatantSwitchPostEntryPlan pinnedContract() {
        return new CombatantSwitchPostEntryPlan(List.of(
                Stage.BALL_FETCH,
                Stage.CURIOUS_MEDICINE,
                Stage.INSERT_REPLACEMENT_INITIATIVE,
                Stage.FIRST_BLOOD,
                Stage.QUICK_SWITCH
        ));
    }

    public enum Stage {
        BALL_FETCH,
        CURIOUS_MEDICINE,
        INSERT_REPLACEMENT_INITIATIVE,
        FIRST_BLOOD,
        QUICK_SWITCH
    }
}
