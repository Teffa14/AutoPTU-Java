package io.autoptu.core.hook;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuickSwitchTriggerPlanOracleParityTest {
    private static final Set<String> QUICK_SWITCH = Set.of("Quick Switch");

    @Test
    void triggeredQuickSwitchPlanMatchesPinnedOracleContract() throws IOException {
        List<String> fixture = fixture();
        assertTrue(fixture.contains("TRIGGER_AP\trequired>=2\tconsume=2"));
        assertTrue(fixture.contains("TRIGGER_PROMPT\tphase=interrupt\toptional=True\tdefault=first_replacement"));
        assertTrue(fixture.contains("TRIGGER_SWITCH\toutgoing_id=actor_id\treplacement_id=choice_id\tinitiator_id=trainer.identifier\tallow_replacement_turn=True\tallow_immediate=False\tallow_quick_switch_triggers=False"));
        assertTrue(fixture.contains("TRIGGER_TEMP\tquick_switch_sent_out\tround=current\texpires=current"));
        assertTrue(fixture.contains("TRIGGER_SOURCE\topponent_send_out\tally_faint"));
        assertTrue(fixture.contains("FAINT_GUARD\tquick_switch_faint_handled\tround=current\texpires=current"));

        SwitchTriggerDecisionPlan opponentSendOut = onlyPlan(context(
                SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT,
                true,
                false,
                2,
                List.of("bench-a", "bench-b"),
                QUICK_SWITCH,
                false
        ));
        assertEquals("Quick Switch", opponentSendOut.featureName());
        assertEquals("actor", opponentSendOut.actorId());
        assertEquals(List.of("bench-a", "bench-b"), opponentSendOut.replacementIds());
        assertEquals("bench-a", opponentSendOut.defaultReplacementId());
        assertEquals(2, opponentSendOut.requiredAp());
        assertEquals(2, opponentSendOut.consumeAp());
        assertEquals("interrupt", opponentSendOut.phase());
        assertTrue(opponentSendOut.optional());
        assertEquals(new SwitchTriggerDecisionPlan.SwitchPolicy(true, false, false), opponentSendOut.switchPolicy());
        assertEquals("quick_switch_sent_out", opponentSendOut.sentOutEffectKey());
        assertEquals("", opponentSendOut.dedupeEffectKey());

        SwitchTriggerDecisionPlan allyFaint = onlyPlan(context(
                SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                true,
                false,
                2,
                List.of("bench-a", "bench-b"),
                QUICK_SWITCH,
                false
        ));
        assertEquals("quick_switch_faint_handled", allyFaint.dedupeEffectKey());
        assertEquals(SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT, allyFaint.trigger());
    }

    @Test
    void triggerPlannerRejectsIneligibleQuickSwitchOffers() {
        assertNoPlan(context(SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT, false, false, 2,
                List.of("bench"), QUICK_SWITCH, false));
        assertNoPlan(context(SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT, true, true, 2,
                List.of("bench"), QUICK_SWITCH, false));
        assertNoPlan(context(SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT, true, false, 1,
                List.of("bench"), QUICK_SWITCH, false));
        assertNoPlan(context(SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT, true, false, 2,
                List.of(), QUICK_SWITCH, false));
        assertNoPlan(context(SwitchTriggerDecisionPlan.Trigger.OPPONENT_SEND_OUT, true, false, 2,
                List.of("bench"), Set.of(), false));
        assertNoPlan(context(SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT, true, false, 2,
                List.of("bench"), QUICK_SWITCH, true));
    }

    private static SwitchTriggerDecisionPlan onlyPlan(SwitchTriggerPlannerRegistry.PlanningContext context) {
        List<SwitchTriggerDecisionPlan> plans = BuiltinSwitchTriggerPlanners.paritySafe().plans(context);
        assertEquals(1, plans.size());
        return plans.getFirst();
    }

    private static void assertNoPlan(SwitchTriggerPlannerRegistry.PlanningContext context) {
        assertTrue(BuiltinSwitchTriggerPlanners.paritySafe().plans(context).isEmpty());
    }

    private static SwitchTriggerPlannerRegistry.PlanningContext context(
            SwitchTriggerDecisionPlan.Trigger trigger,
            boolean active,
            boolean fainted,
            int ap,
            List<String> replacements,
            Set<String> features,
            boolean alreadyHandled
    ) {
        return new SwitchTriggerPlannerRegistry.PlanningContext(
                trigger,
                "actor",
                active,
                fainted,
                ap,
                replacements,
                features,
                alreadyHandled
        );
    }

    private static List<String> fixture() throws IOException {
        Path fixture = Path.of("build/oracle/quick-switch-entry-contract.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        return Files.readAllLines(fixture);
    }
}
