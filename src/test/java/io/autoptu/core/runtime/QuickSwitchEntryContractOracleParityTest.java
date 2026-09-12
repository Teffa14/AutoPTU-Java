package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class QuickSwitchEntryContractOracleParityTest {
    @Test
    void pinnedQuickSwitchEntryContractRemainsExplicit() throws IOException {
        Path fixture = Path.of("build/oracle/quick-switch-entry-contract.tsv");
        Assumptions.assumeTrue(Files.exists(fixture));

        assertEquals(List.of(
                "ACTION_AP\t1 if actor.has_trainer_feature('Juggler') else 2",
                "ACTION_SWITCH\toutgoing_id=self.actor_id\treplacement_id=self.replacement_id\tinitiator_id=trainer.identifier\tapply_tag_in=True\tallow_replacement_turn=True\tallow_immediate=False\tallow_quick_switch_triggers=False",
                "ACTION_TEMP\tquick_switch_sent_out\tround=current\texpires=current",
                "ACTION_EVENT\ttype=trainer_feature\tfeature=Quick Switch\teffect=switch\tap_cost=2",
                "ACTION_ORDER\tconsume_ap\tapply_switch\tquick_switch_sent_out\ttrainer_feature_event",
                "TRIGGER_AP\trequired>=2\tconsume=2",
                "TRIGGER_PROMPT\tphase=interrupt\toptional=True\tdefault=first_replacement",
                "TRIGGER_SWITCH\toutgoing_id=actor_id\treplacement_id=choice_id\tinitiator_id=trainer.identifier\tallow_replacement_turn=True\tallow_immediate=False\tallow_quick_switch_triggers=False",
                "TRIGGER_TEMP\tquick_switch_sent_out\tround=current\texpires=current",
                "TRIGGER_EVENT\ttype=trainer_feature\tfeature=Quick Switch\teffect=switch\ttrigger=propagated\tap_cost=2",
                "TRIGGER_ORDER\tconsume_ap\tapply_switch\tquick_switch_sent_out\ttrainer_feature_event",
                "TRIGGER_SOURCE\topponent_send_out\tally_faint",
                "FAINT_GUARD\tquick_switch_faint_handled\tround=current\texpires=current",
                "FAINT_GUARD_ORDER\tcheck_guard\tarm_guard\tdispatch_quick_switch"
        ), Files.readAllLines(fixture));
    }
}
