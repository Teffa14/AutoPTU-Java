package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orders already-eligible reaction windows for deterministic runtime processing.
 *
 * <p>Arbitration is deliberately separate from discovery and execution. Authoritative event
 * occurrence order wins first; reactors tied to the same occurrence retain battle insertion
 * order. This class does not choose whether a reaction is taken, commit usage, consume action
 * resources, roll RNG, or execute a reaction.</p>
 */
public final class RuntimeReactionWindowArbiter {
    private static final String SEQUENCE_PREFIX = "event-sequence=";
    private static final String EVENT_SEPARATOR = "|event=";

    private final Map<String, Integer> reactorInsertionRank;

    public RuntimeReactionWindowArbiter(BattleRuntimeState battleState) {
        Objects.requireNonNull(battleState, "battleState");
        LinkedHashMap<String, Integer> ranks = new LinkedHashMap<>();
        int rank = 0;
        for (String combatantId : battleState.combatantIds()) {
            ranks.put(combatantId, rank++);
        }
        this.reactorInsertionRank = Map.copyOf(ranks);
    }

    /**
     * Returns a stable arbitration queue without mutating the supplied candidates.
     *
     * <p>Only occurrence-backed windows are accepted. Semantic-payload-only windows do not carry
     * enough battle-local identity to arbitrate safely.</p>
     */
    public List<RuntimeReactionWindowResolver.Candidate> arbitrate(
            List<RuntimeReactionWindowResolver.Candidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        ArrayList<RuntimeReactionWindowResolver.Candidate> ordered = new ArrayList<>(candidates.size());
        for (RuntimeReactionWindowResolver.Candidate candidate : candidates) {
            RuntimeReactionWindowResolver.Candidate required = Objects.requireNonNull(candidate, "candidate");
            if (!reactorInsertionRank.containsKey(required.reactorId())) {
                throw new IllegalArgumentException("reaction candidate reactor is not in battle state: " + required.reactorId());
            }
            occurrenceSequence(required.window());
            ordered.add(required);
        }
        ordered.sort(Comparator
                .comparingLong((RuntimeReactionWindowResolver.Candidate candidate) -> occurrenceSequence(candidate.window()))
                .thenComparingInt(candidate -> reactorInsertionRank.get(candidate.reactorId()))
                .thenComparing(candidate -> candidate.window().windowKey()));
        return List.copyOf(ordered);
    }

    static long occurrenceSequence(RuntimeReactionWindow window) {
        Objects.requireNonNull(window, "window");
        String key = window.triggeringEventKey();
        if (!key.startsWith(SEQUENCE_PREFIX)) {
            throw new IllegalArgumentException("reaction arbitration requires occurrence-backed windows");
        }
        int separator = key.indexOf(EVENT_SEPARATOR, SEQUENCE_PREFIX.length());
        if (separator < 0) {
            throw new IllegalArgumentException("invalid triggering occurrence key: " + key);
        }
        String rawSequence = key.substring(SEQUENCE_PREFIX.length(), separator);
        try {
            long sequence = Long.parseLong(rawSequence);
            if (sequence < 1) {
                throw new IllegalArgumentException("reaction occurrence sequence must be positive");
            }
            return sequence;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid reaction occurrence sequence: " + rawSequence, exception);
        }
    }
}
