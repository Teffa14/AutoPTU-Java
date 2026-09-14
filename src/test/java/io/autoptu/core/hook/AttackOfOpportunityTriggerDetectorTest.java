package io.autoptu.core.hook;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackOfOpportunityTriggerDetectorTest {
    @Test
    void detectsAllFivePinnedOracleTriggerFamilies() {
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_USES_NON_TARGETING_MANEUVER),
                detect(true, AttackOfOpportunityTriggerDetector.Kind.MANEUVER, "Dirty Trick", false, false, false, false)
        );
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_STANDS_UP),
                detect(true, AttackOfOpportunityTriggerDetector.Kind.STAND_UP, "", false, false, false, false)
        );
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_USES_RANGED_ATTACK_WITHOUT_ADJACENT_TARGET),
                detect(true, AttackOfOpportunityTriggerDetector.Kind.RANGED_ATTACK, "", false, false, false, false)
        );
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_RETRIEVES_ITEM),
                detect(true, AttackOfOpportunityTriggerDetector.Kind.ITEM_RETRIEVAL, "", false, false, true, false)
        );
        assertEquals(
                Optional.of(ActionWindowTrigger.ADJACENT_FOE_SHIFTS_AWAY),
                detect(true, AttackOfOpportunityTriggerDetector.Kind.SHIFT, "", false, false, false, true)
        );
    }

    @Test
    void enforcesNegativeConditionsFromOracleText() {
        assertEquals(Optional.empty(), detect(false, AttackOfOpportunityTriggerDetector.Kind.STAND_UP, "", false, false, false, false));
        assertEquals(Optional.empty(), detect(true, AttackOfOpportunityTriggerDetector.Kind.MANEUVER, "Push", true, false, false, false));
        assertEquals(Optional.empty(), detect(true, AttackOfOpportunityTriggerDetector.Kind.MANEUVER, "Feint", false, false, false, false));
        assertEquals(Optional.empty(), detect(true, AttackOfOpportunityTriggerDetector.Kind.RANGED_ATTACK, "", false, true, false, false));
        assertEquals(Optional.empty(), detect(true, AttackOfOpportunityTriggerDetector.Kind.ITEM_RETRIEVAL, "", false, false, false, false));
        assertEquals(Optional.empty(), detect(true, AttackOfOpportunityTriggerDetector.Kind.SHIFT, "", false, false, false, false));
    }

    @Test
    void recognizesEachNamedManeuverCaseInsensitively() {
        for (String maneuver : new String[]{"Push", "Grapple", "Disarm", "Trip", "Dirty Trick"}) {
            assertEquals(
                    Optional.of(ActionWindowTrigger.ADJACENT_FOE_USES_NON_TARGETING_MANEUVER),
                    detect(true, AttackOfOpportunityTriggerDetector.Kind.MANEUVER, maneuver, false, false, false, false)
            );
        }
    }

    private static Optional<ActionWindowTrigger> detect(
            boolean adjacent,
            AttackOfOpportunityTriggerDetector.Kind kind,
            String actionName,
            boolean targetsReactor,
            boolean rangedTargetsAdjacentCombatant,
            boolean standardAction,
            boolean shiftsAway
    ) {
        return AttackOfOpportunityTriggerDetector.detect(new AttackOfOpportunityTriggerDetector.Input(
                adjacent,
                kind,
                actionName,
                targetsReactor,
                rangedTargetsAdjacentCombatant,
                standardAction,
                shiftsAway
        ));
    }
}
