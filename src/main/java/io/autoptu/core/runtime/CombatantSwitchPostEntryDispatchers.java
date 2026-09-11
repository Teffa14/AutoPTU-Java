package io.autoptu.core.runtime;

/**
 * Production registry for switch post-entry families whose Python contracts are already frozen and
 * covered by differential tests.
 *
 * <p>Only proven families belong here. Missing stages intentionally remain pending in
 * {@link CombatantSwitchPostEntryDispatcher} until their own Python behavior is frozen.</p>
 */
public final class CombatantSwitchPostEntryDispatchers {
    private static final String BALL_FETCH_DESCRIPTION =
            "Ball Fetch pulls the user toward the new combatant.";
    private static final String CURIOUS_MEDICINE_DESCRIPTION =
            "Curious Medicine resets combat stages on entry.";

    private CombatantSwitchPostEntryDispatchers() {
    }

    public static CombatantSwitchPostEntryDispatcher paritySafe() {
        return CombatantSwitchPostEntryDispatcher.empty()
                .withHandler(
                        CombatantSwitchPostEntryPlan.Stage.BALL_FETCH,
                        new AbilityApproachShiftPostEntryHandler(
                                "Ball Fetch",
                                "ball_fetch_shift",
                                BALL_FETCH_DESCRIPTION
                        )
                )
                .withHandler(
                        CombatantSwitchPostEntryPlan.Stage.CURIOUS_MEDICINE,
                        new AbilityCombatStageResetPostEntryHandler(
                                "Curious Medicine",
                                "curious_medicine_used",
                                2,
                                CURIOUS_MEDICINE_DESCRIPTION
                        )
                )
                .withHandler(
                        CombatantSwitchPostEntryPlan.Stage.INSERT_REPLACEMENT_INITIATIVE,
                        new ReplacementInitiativePostEntryHandler()
                );
    }
}
