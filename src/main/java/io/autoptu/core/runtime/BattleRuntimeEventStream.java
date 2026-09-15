package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;

import java.util.List;
import java.util.Objects;

/**
 * Battle-local owner of semantic event occurrence identity.
 *
 * <p>One instance belongs to one authoritative battle runtime. Callers submit completed
 * action results; this boundary preserves their event order and assigns monotonically
 * increasing occurrence identities through one private sequencer. Minecraft/Cobblemon
 * adapters must consume the resulting occurrences rather than minting their own IDs.</p>
 */
public final class BattleRuntimeEventStream {
    private final RuntimeBattleEventSequencer sequencer = new RuntimeBattleEventSequencer();

    /** Records one completed authoritative action result in its existing semantic order. */
    public List<BattleEventOccurrence> record(AppliedActionResult result) {
        Objects.requireNonNull(result, "result");
        return result.recordEventOccurrences(sequencer);
    }

    /** Next battle-local sequence, exposed read-only for snapshots/tests. */
    public long nextSequence() {
        return sequencer.nextSequence();
    }
}
