package io.autoptu.core.runtime;

import io.autoptu.core.event.BattleEventOccurrence;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Battle-local runtime boundary that keeps authoritative state and semantic event
 * occurrence identity under one owner.
 *
 * <p>Adapters may receive the state snapshot and emitted occurrences, but they do not
 * receive the mutable event sequencer. All completed action results pass through this
 * context so occurrence identities remain monotonic for the lifetime of one battle.</p>
 */
public final class BattleRuntimeExecutionContext {
    private final BattleRuntimeState state;
    private final BattleRuntimeEventStream eventStream;

    public BattleRuntimeExecutionContext(BattleRuntimeState state) {
        this.state = Objects.requireNonNull(state, "state");
        this.eventStream = new BattleRuntimeEventStream();
    }

    public BattleRuntimeState state() {
        return state;
    }

    /** Records one completed authoritative action result in semantic order. */
    public List<BattleEventOccurrence> record(AppliedActionResult result) {
        return eventStream.record(result);
    }

    /**
     * Executes one authoritative action application and immediately assigns occurrence
     * identities to its semantic events before the result leaves the battle-local boundary.
     *
     * <p>The supplier owns rule execution and state mutation. This method owns only the
     * event-occurrence boundary; it does not reinterpret legality, RNG, action economy, or
     * event payloads.</p>
     */
    public RecordedActionResult applyAndRecord(Supplier<AppliedActionResult> application) {
        Objects.requireNonNull(application, "application");
        AppliedActionResult result = Objects.requireNonNull(application.get(), "application result");
        return new RecordedActionResult(result, eventStream.record(result));
    }

    /** Read-only sequence observation for runtime diagnostics and deterministic tests. */
    public long nextEventSequence() {
        return eventStream.nextSequence();
    }
}
