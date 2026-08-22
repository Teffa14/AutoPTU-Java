package io.autoptu.core.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoundTrainerFeatureLifecyclePolicyOracleParityTest {
    @Test
    void javaContractMatchesPinnedPythonRoundStartOrdering() throws IOException {
        Path fixturePath = Path.of("build/oracle/trainer-round-feature-contract.tsv");
        Assumptions.assumeTrue(Files.exists(fixturePath));
        Map<String, Integer> fixture = readFixture(fixturePath);
        RoundTrainerFeatureLifecyclePolicy policy = RoundTrainerFeatureLifecyclePolicy.pythonParityContract();

        assertEquals(fixture.get("clears_declared_actions"), bit(policy.clearsDeclaredActions()));
        assertEquals(fixture.get("declared_actions_after_trainer_reset"), bit(policy.declaredActionsAfterTrainerReset()));
        assertEquals(fixture.get("declared_actions_before_initial_sendout"), bit(policy.declaredActionsBeforeInitialSendout()));
        assertEquals(fixture.get("initial_sendout_round_one_only"), bit(policy.initialSendoutRoundOneOnly()));
        assertEquals(fixture.get("initial_sendout_requires_active_pokemon"), bit(policy.initialSendoutRequiresActivePokemon()));
        assertEquals(fixture.get("initial_sendout_skips_fainted_pokemon"), bit(policy.initialSendoutSkipsFaintedPokemon()));
        assertEquals(fixture.get("initial_sendout_uses_initial_setup"), bit(policy.initialSendoutUsesInitialSetup()));
        assertEquals(fixture.get("initiative_rebuild_before_round_start_event"), bit(policy.initiativeRebuildBeforeRoundStartEvent()));
        assertEquals(fixture.get("round_start_event_before_feature_dispatch"), bit(policy.roundStartEventBeforeFeatureDispatch()));
        assertEquals(fixture.get("dispatches_round_start_trigger"), bit(policy.dispatchesRoundStartTrigger()));
        assertEquals(fixture.get("round_start_payload_uses_current_round"), bit(policy.roundStartPayloadUsesCurrentRound()));
    }

    private static int bit(boolean value) {
        return value ? 1 : 0;
    }

    private static Map<String, Integer> readFixture(Path path) throws IOException {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\t", 2);
            values.put(parts[0], Integer.parseInt(parts[1]));
        }
        return values;
    }
}
