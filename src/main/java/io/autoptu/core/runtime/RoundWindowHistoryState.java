package io.autoptu.core.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-core state for round-indexed histories whose retention window is declarative.
 *
 * <p>This store intentionally models a behavior family instead of one Java field per
 * move. The pinned Python oracle currently uses this family for Echoed Voice, Fusion
 * Bolt, and Fusion Flare.</p>
 */
public final class RoundWindowHistoryState {
    public static final String ECHOED_VOICE_ROUNDS = "echoed_voice_rounds";
    public static final String FUSION_BOLT_ROUNDS = "fusion_bolt_rounds";
    public static final String FUSION_FLARE_ROUNDS = "fusion_flare_rounds";

    public record Definition(String historyId, int roundsBack) {
        public Definition {
            if (historyId == null || historyId.isBlank()) {
                throw new IllegalArgumentException("historyId is required");
            }
            historyId = historyId.strip();
            if (roundsBack < 0) {
                throw new IllegalArgumentException("roundsBack must be non-negative");
            }
        }
    }

    private final LinkedHashMap<String, Definition> definitions = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<Integer>> roundsByHistory = new LinkedHashMap<>();

    public RoundWindowHistoryState(Collection<Definition> definitions) {
        for (Definition definition : definitions == null ? List.<Definition>of() : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("history definition must be non-null");
            }
            Definition previous = this.definitions.putIfAbsent(definition.historyId(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate historyId: " + definition.historyId());
            }
            roundsByHistory.put(definition.historyId(), List.of());
        }
    }

    /** Built-in definitions frozen from the pinned Python PhaseController.start_round contract. */
    public static RoundWindowHistoryState pythonMoveHistories() {
        return new RoundWindowHistoryState(List.of(
                new Definition(ECHOED_VOICE_ROUNDS, 2),
                new Definition(FUSION_BOLT_ROUNDS, 1),
                new Definition(FUSION_FLARE_ROUNDS, 1)
        ));
    }

    public List<Definition> definitions() {
        return List.copyOf(definitions.values());
    }

    public List<String> historyIds() {
        return List.copyOf(definitions.keySet());
    }

    public List<Integer> rounds(String historyId) {
        requireDefinition(historyId);
        return roundsByHistory.get(historyId);
    }

    public Map<String, List<Integer>> snapshot() {
        LinkedHashMap<String, List<Integer>> copy = new LinkedHashMap<>();
        for (String historyId : definitions.keySet()) {
            copy.put(historyId, roundsByHistory.get(historyId));
        }
        return Map.copyOf(copy);
    }

    /** Runtime-owned materialization boundary for canonical battle restoration and fixtures. */
    void replaceRoundsFromRuntime(String historyId, Collection<Integer> rounds) {
        requireDefinition(historyId);
        ArrayList<Integer> copy = new ArrayList<>();
        for (Integer round : rounds == null ? List.<Integer>of() : rounds) {
            if (round == null || round < 0) {
                throw new IllegalArgumentException("round history entries must be non-negative");
            }
            copy.add(round);
        }
        roundsByHistory.put(historyId, List.copyOf(copy));
    }

    /** Runtime-owned producer boundary used by move-special execution. */
    void recordRoundFromRuntime(String historyId, int round) {
        requireDefinition(historyId);
        if (round < 0) {
            throw new IllegalArgumentException("round must be non-negative");
        }
        ArrayList<Integer> updated = new ArrayList<>(roundsByHistory.get(historyId));
        updated.add(round);
        roundsByHistory.put(historyId, List.copyOf(updated));
    }

    /** Lifecycle-only pruning boundary. Preserves insertion order and duplicate rounds. */
    void pruneForRoundFromLifecycle(int currentRound) {
        if (currentRound < 0) {
            throw new IllegalArgumentException("currentRound must be non-negative");
        }
        for (Definition definition : definitions.values()) {
            roundsByHistory.put(
                    definition.historyId(),
                    RoundWindowHistoryPruning.retain(
                            roundsByHistory.get(definition.historyId()),
                            currentRound,
                            definition.roundsBack()
                    )
            );
        }
    }

    private void requireDefinition(String historyId) {
        if (historyId == null || !definitions.containsKey(historyId)) {
            throw new IllegalArgumentException("unknown round-window history: " + historyId);
        }
    }
}
