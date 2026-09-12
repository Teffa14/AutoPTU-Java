package io.autoptu.core.runtime;

import io.autoptu.core.hook.SwitchTriggerDecisionPlan;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SwitchTriggerInterruptResponseResolverOracleParityTest {
    @Test
    void resolvesPinnedQuickSwitchInterruptResponseSemantics() throws IOException {
        List<String> fixture = fixture();
        assertTrue(fixture.contains(
                "TRIGGER_RESPONSE\tfalsy=decline\ttruthy_non_dict=accept_default\tdict_accept_default=True\tdict_accept_false=decline"
        ));
        assertTrue(fixture.contains(
                "TRIGGER_CHOICE\tdefault=first_replacement\tfield=choice\tlegal_requested=selected\tinvalid_requested=default_first"
        ));

        SwitchTriggerDecisionPlan plan = plan();

        var falsy = SwitchTriggerInterruptResponseResolver.resolve(
                plan,
                SwitchTriggerInterruptResponseResolver.Response.falsy()
        );
        assertFalse(falsy.accepted());
        assertEquals("", falsy.replacementId());

        var truthyNonMapping = SwitchTriggerInterruptResponseResolver.resolve(
                plan,
                SwitchTriggerInterruptResponseResolver.Response.truthyNonMapping()
        );
        assertTrue(truthyNonMapping.accepted());
        assertEquals("bench-a", truthyNonMapping.replacementId());

        var omittedAccept = SwitchTriggerInterruptResponseResolver.resolve(
                plan,
                SwitchTriggerInterruptResponseResolver.Response.mapping(null, null)
        );
        assertTrue(omittedAccept.accepted());
        assertEquals("bench-a", omittedAccept.replacementId());

        var explicitDecline = SwitchTriggerInterruptResponseResolver.resolve(
                plan,
                SwitchTriggerInterruptResponseResolver.Response.mapping(false, "bench-b")
        );
        assertFalse(explicitDecline.accepted());
        assertEquals("", explicitDecline.replacementId());

        var legalChoice = SwitchTriggerInterruptResponseResolver.resolve(
                plan,
                SwitchTriggerInterruptResponseResolver.Response.mapping(true, "bench-b")
        );
        assertTrue(legalChoice.accepted());
        assertEquals("bench-b", legalChoice.replacementId());

        var invalidChoice = SwitchTriggerInterruptResponseResolver.resolve(
                plan,
                SwitchTriggerInterruptResponseResolver.Response.mapping(true, "not-a-candidate")
        );
        assertTrue(invalidChoice.accepted());
        assertEquals("bench-a", invalidChoice.replacementId());
    }

    private static SwitchTriggerDecisionPlan plan() {
        return new SwitchTriggerDecisionPlan(
                "Quick Switch",
                SwitchTriggerDecisionPlan.Trigger.ALLY_FAINT,
                "actor",
                List.of("bench-a", "bench-b"),
                "bench-a",
                2,
                2,
                "interrupt",
                true,
                new SwitchTriggerDecisionPlan.SwitchPolicy(true, false, false),
                "quick_switch_sent_out",
                "quick_switch_faint_handled"
        );
    }

    private static List<String> fixture() throws IOException {
        Path fixture = Path.of("build/oracle/quick-switch-entry-contract.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));
        return Files.readAllLines(fixture);
    }
}
